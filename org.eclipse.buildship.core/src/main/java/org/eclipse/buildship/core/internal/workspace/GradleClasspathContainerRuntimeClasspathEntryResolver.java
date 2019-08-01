/*
 * Copyright (c) 2016 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.eclipse.buildship.core.internal.workspace;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.IRuntimeClasspathEntry;
import org.eclipse.jdt.launching.IRuntimeClasspathEntryResolver;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;

import org.eclipse.buildship.core.internal.CorePlugin;
import org.eclipse.buildship.core.internal.GradlePluginsRuntimeException;
import org.eclipse.buildship.core.internal.launch.GradleClasspathProvider;
import org.eclipse.buildship.core.internal.launch.LaunchConfigurationScope;
import org.eclipse.buildship.core.internal.preferences.PersistentModel;
import org.eclipse.buildship.core.internal.util.eclipse.PlatformUtils;

/**
 * {@link IRuntimeClasspathEntryResolver} implementation to resolve Gradle classpath container
 * entries.
 *
 * @author Donat Csikos
 */
public final class GradleClasspathContainerRuntimeClasspathEntryResolver implements IRuntimeClasspathEntryResolver {

    @Override
    public IRuntimeClasspathEntry[] resolveRuntimeClasspathEntry(IRuntimeClasspathEntry entry, ILaunchConfiguration configuration) throws CoreException {
        if (entry == null || entry.getJavaProject() == null) {
            return new IRuntimeClasspathEntry[0];
        }
        LaunchConfigurationScope configurationScopes = LaunchConfigurationScope.from(configuration);
        // IJavaLaunchConfigurationConstants.ATTR_EXCLUDE_TEST_CODE not available in Eclipse 4.3
        boolean excludeTestCode = configuration.getAttribute("org.eclipse.jdt.launching.ATTR_EXCLUDE_TEST_CODE", false);
        return resolveRuntimeClasspathEntry(entry, entry.getJavaProject(), configurationScopes, excludeTestCode);
    }

    @Override
    public IRuntimeClasspathEntry[] resolveRuntimeClasspathEntry(IRuntimeClasspathEntry entry, IJavaProject project) throws CoreException {
        return resolveRuntimeClasspathEntry(entry, project, LaunchConfigurationScope.INCLUDE_ALL, false);
    }

    // @Override commented out as this method doesn't exist older Eclipse versions
    public IRuntimeClasspathEntry[] resolveRuntimeClasspathEntry(IRuntimeClasspathEntry entry, IJavaProject project, boolean excludeTestCode) throws CoreException {
        return resolveRuntimeClasspathEntry(entry, project, LaunchConfigurationScope.INCLUDE_ALL, excludeTestCode);
    }

    private IRuntimeClasspathEntry[] resolveRuntimeClasspathEntry(IRuntimeClasspathEntry entry, IJavaProject project, LaunchConfigurationScope configurationScopes,
            boolean excludeTestCode) throws CoreException {
        if (entry.getType() != IRuntimeClasspathEntry.CONTAINER || !entry.getPath().equals(GradleClasspathContainer.CONTAINER_PATH)) {
            return new IRuntimeClasspathEntry[0];
        }

        PersistentModel model = CorePlugin.modelPersistence().loadModel(project.getProject());
        if (!model.isPresent()) {
            throw new GradlePluginsRuntimeException("Model not available for " + project.getProject().getName());
        }

        // Eclipse 4.3 (Kepler) doesn't support test attributes, so for that case we fall back to custom scope attributes
        if (model.getGradleVersion().supportsTestAttributes() && PlatformUtils.supportsTestAttributes()) {
            return runtimeClasspathWithTestSources(project, excludeTestCode);
        } else {
            return runtimeClasspathWithGradleScopes(project, configurationScopes, excludeTestCode);
        }
    }

    private IRuntimeClasspathEntry[] runtimeClasspathWithGradleScopes(IJavaProject project, LaunchConfigurationScope configurationScopes, boolean excludeTestCode)
            throws CoreException {
        List<IRuntimeClasspathEntry> result = Lists.newArrayList();
        collectContainerRuntimeClasspathWithGradleScopes(project, result, false, configurationScopes);
        return result.toArray(new IRuntimeClasspathEntry[result.size()]);
    }

    private void collectContainerRuntimeClasspathWithGradleScopes(IJavaProject project, List<IRuntimeClasspathEntry> result, boolean includeExportedEntriesOnly,
            LaunchConfigurationScope configurationScopes) throws CoreException {
        IClasspathContainer container = JavaCore.getClasspathContainer(GradleClasspathContainer.CONTAINER_PATH, project);
        if (container != null) {
            collectContainerRuntimeClasspathWithGradleScopes(container, result, includeExportedEntriesOnly, configurationScopes);
        }
    }

    private void collectContainerRuntimeClasspathWithGradleScopes(IClasspathContainer container, List<IRuntimeClasspathEntry> result, boolean includeExportedEntriesOnly,
            LaunchConfigurationScope configurationScopes) throws CoreException {
        for (final IClasspathEntry cpe : container.getClasspathEntries()) {
            if (!includeExportedEntriesOnly || cpe.isExported()) {
                if (cpe.getEntryKind() == IClasspathEntry.CPE_LIBRARY && configurationScopes.isEntryIncluded(cpe)) {
                    result.add(JavaRuntime.newArchiveRuntimeClasspathEntry(cpe.getPath()));
                } else if (cpe.getEntryKind() == IClasspathEntry.CPE_PROJECT) {
                    Optional<IProject> candidate = findAccessibleJavaProject(cpe.getPath().segment(0));
                    if (candidate.isPresent()) {
                        IJavaProject dependencyProject = JavaCore.create(candidate.get());
                        IRuntimeClasspathEntry projectRuntimeEntry = JavaRuntime.newProjectRuntimeClasspathEntry(dependencyProject);
                        // add the project entry itself so that the source lookup can find the
                        // classes. See https://github.com/eclipse/buildship/issues/383
                        result.add(projectRuntimeEntry);
                        Collections.addAll(result, GradleClasspathProvider.resolveOutputLocations(projectRuntimeEntry, dependencyProject, configurationScopes));
                        collectContainerRuntimeClasspathWithGradleScopes(dependencyProject, result, true, configurationScopes);
                    }
                }
            }
        }
    }

    private IRuntimeClasspathEntry[] runtimeClasspathWithTestSources(IJavaProject project, boolean excludeTestCode) throws CoreException {
        List<IRuntimeClasspathEntry> result = Lists.newArrayList();

        IClasspathContainer container = JavaCore.getClasspathContainer(GradleClasspathContainer.CONTAINER_PATH, project);
        if (container == null) {
            return new IRuntimeClasspathEntry[0];
        }

        for (final IClasspathEntry cpe : container.getClasspathEntries()) {
            if (cpe.getEntryKind() == IClasspathEntry.CPE_LIBRARY && !(excludeTestCode && hasTestAttribute(cpe))) {
                result.add(JavaRuntime.newArchiveRuntimeClasspathEntry(cpe.getPath()));
            } else if (cpe.getEntryKind() == IClasspathEntry.CPE_PROJECT) {
                Optional<IProject> candidate = findAccessibleJavaProject(cpe.getPath().segment(0));
                if (candidate.isPresent()) {
                    IJavaProject dependencyProject = JavaCore.create(candidate.get());
                    IRuntimeClasspathEntry projectRuntimeEntry = JavaRuntime.newProjectRuntimeClasspathEntry(dependencyProject);
                    // add the project entry itself so that the source lookup can find the classes
                    // see https://github.com/eclipse/buildship/issues/383
                    result.add(projectRuntimeEntry);
                    Collections.addAll(result, invokeJavaRuntimeResolveRuntimeClasspathEntry(projectRuntimeEntry, dependencyProject, excludeTestCode));
                }
            }
        }
        return result.toArray(new IRuntimeClasspathEntry[result.size()]);
    }

    private static IRuntimeClasspathEntry[] invokeJavaRuntimeResolveRuntimeClasspathEntry(IRuntimeClasspathEntry projectRuntimeEntry, IJavaProject dependencyProject, boolean excludeTestCode) throws CoreException{
        // JavaRuntime.resolveRuntimeClasspathEntry is available since Eclipse 4.8
        try {
            Method method = JavaRuntime.class.getMethod("resolveRuntimeClasspathEntry", IRuntimeClasspathEntry.class, IJavaProject.class, boolean.class);
            return (IRuntimeClasspathEntry[]) method.invoke(null, projectRuntimeEntry, dependencyProject, excludeTestCode);
        } catch (Exception e) {
            throw new GradlePluginsRuntimeException("JavaRuntime.resolveRuntimeClasspathEntry() should not be called when Buildship is installed for Eclipse 4.8", e);
        }
    }

    private boolean hasTestAttribute(IClasspathEntry entry) {
        for (IClasspathAttribute a : entry.getExtraAttributes()) {
            if ("test".equals(a.getName()) && Boolean.valueOf(a.getValue())) {
                return true;
            }
        }
        return false;
    }

    private static Optional<IProject> findAccessibleJavaProject(String name) {
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(name);
        if (project != null && project.isAccessible() && hasJavaNature(project)) {
            return Optional.of(project);
        } else {
            return Optional.absent();
        }
    }

    private static boolean hasJavaNature(IProject project) {
        try {
            return project.hasNature(JavaCore.NATURE_ID);
        } catch (CoreException e) {
            return false;
        }
    }

    @Override
    public IVMInstall resolveVMInstall(IClasspathEntry entry) throws CoreException {
        return null;
    }

}