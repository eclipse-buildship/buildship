/*******************************************************************************
 * Copyright (c) 2023 Gradle Inc. and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.core.internal.workspace;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
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
        return resolveRuntimeClasspathEntry(entry, entry.getJavaProject(), configurationScopes, excludeTestCode, hasModuleSupport());
    }

    @Override
    public IRuntimeClasspathEntry[] resolveRuntimeClasspathEntry(IRuntimeClasspathEntry entry, IJavaProject project) throws CoreException {
        return resolveRuntimeClasspathEntry(entry, entry.getJavaProject(), LaunchConfigurationScope.INCLUDE_ALL, false, hasModuleSupport());
    }

    // @Override commented out as this method doesn't exist older Eclipse versions
    public IRuntimeClasspathEntry[] resolveRuntimeClasspathEntry(IRuntimeClasspathEntry entry, IJavaProject project, boolean excludeTestCode) throws CoreException {
        return resolveRuntimeClasspathEntry(entry, entry.getJavaProject(), LaunchConfigurationScope.INCLUDE_ALL, excludeTestCode, hasModuleSupport());
    }

    private static boolean hasModuleSupport() {
        return CorePlugin.configurationManager().loadWorkspaceConfiguration().isExperimentalModuleSupportEnabled();
    }

    private IRuntimeClasspathEntry[] resolveRuntimeClasspathEntry(IRuntimeClasspathEntry entry, IJavaProject project, LaunchConfigurationScope configurationScopes,
            boolean excludeTestCode, boolean moduleSupport) throws CoreException {
        if (entry.getType() != IRuntimeClasspathEntry.CONTAINER || !entry.getPath().equals(GradleClasspathContainer.CONTAINER_PATH)) {
            return new IRuntimeClasspathEntry[0];
        }
        PersistentModel model = CorePlugin.modelPersistence().loadModel(project.getProject());
        if (!model.isPresent()) {
            throw new GradlePluginsRuntimeException("Model not available for " + project.getProject().getName());
        }

        // Eclipse 4.3 (Kepler) doesn't support test attributes, so for that case we fall back to custom scope attributes
        IRuntimeClasspathEntry[] result;
        if (model.getGradleVersion().supportsTestAttributes() && PlatformUtils.supportsTestAttributes()) {
            result = runtimeClasspathWithTestSources(project, excludeTestCode, moduleSupport);
        } else {
            result = runtimeClasspathWithGradleScopes(project, configurationScopes, excludeTestCode, moduleSupport);
        }

        return Arrays.stream(result).distinct().toArray(IRuntimeClasspathEntry[]::new);
    }

    private IRuntimeClasspathEntry[] runtimeClasspathWithGradleScopes(IJavaProject project, LaunchConfigurationScope configurationScopes, boolean excludeTestCode, boolean modular)
            throws CoreException {
        List<IRuntimeClasspathEntry> result = Lists.newArrayList();
        collectContainerRuntimeClasspathWithGradleScopes(project, result, false,modular, configurationScopes);
        return result.toArray(new IRuntimeClasspathEntry[result.size()]);
    }

    private void collectContainerRuntimeClasspathWithGradleScopes(IJavaProject project, List<IRuntimeClasspathEntry> result, boolean includeExportedEntriesOnly,
            boolean modular,
            LaunchConfigurationScope configurationScopes) throws CoreException {
        IClasspathContainer container = JavaCore.getClasspathContainer(GradleClasspathContainer.CONTAINER_PATH, project);
        if (container != null) {
            collectContainerRuntimeClasspathWithGradleScopes(container, result, includeExportedEntriesOnly, configurationScopes, modular);
        }
    }

    private void collectContainerRuntimeClasspathWithGradleScopes(IClasspathContainer container, List<IRuntimeClasspathEntry> result, boolean includeExportedEntriesOnly,
            LaunchConfigurationScope configurationScopes, boolean modular) throws CoreException {

        for (final IClasspathEntry cpe : container.getClasspathEntries()) {
            if (!includeExportedEntriesOnly || cpe.isExported()) {
                if (cpe.getEntryKind() == IClasspathEntry.CPE_LIBRARY && configurationScopes.isEntryIncluded(cpe)) {
                    result.add(getRuntimeClasspathEntry(cpe.getPath(), modular, "Archive", JavaRuntime::newArchiveRuntimeClasspathEntry, IPath.class, cpe));
                } else if (cpe.getEntryKind() == IClasspathEntry.CPE_PROJECT) {
                    Optional<IProject> candidate = findAccessibleJavaProject(cpe.getPath().segment(0));
                    if (candidate.isPresent()) {
                        IJavaProject dependencyProject = JavaCore.create(candidate.get());
                        IRuntimeClasspathEntry projectRuntimeEntry = getRuntimeClasspathEntry(dependencyProject, modular, "Project", JavaRuntime::newProjectRuntimeClasspathEntry, IJavaProject.class, cpe);
                        // add the project entry itself so that the source lookup can find the
                        // classes. See https://github.com/eclipse/buildship/issues/383
                        result.add(projectRuntimeEntry);
                        Collections.addAll(result, GradleClasspathProvider.resolveOutputLocations(projectRuntimeEntry, dependencyProject, configurationScopes));
                        collectContainerRuntimeClasspathWithGradleScopes(dependencyProject, result, true, modular, configurationScopes);
                    }
                }
            }
        }
    }

    private IRuntimeClasspathEntry[] runtimeClasspathWithTestSources(IJavaProject project, boolean excludeTestCode, boolean moduleSuppport) throws CoreException {
        List<IRuntimeClasspathEntry> result = Lists.newArrayList();

        IClasspathContainer container = JavaCore.getClasspathContainer(GradleClasspathContainer.CONTAINER_PATH, project);
        if (container == null) {
            return new IRuntimeClasspathEntry[0];
        }

        for (final IClasspathEntry cpe : container.getClasspathEntries()) {
            if (excludeTestCode && hasTestAttribute(cpe)) {
                continue;
            }
            if (cpe.getEntryKind() == IClasspathEntry.CPE_LIBRARY) {
                result.add(getRuntimeClasspathEntry(cpe.getPath(), moduleSuppport, "Archive", JavaRuntime::newArchiveRuntimeClasspathEntry, IPath.class, cpe));
            } else if (cpe.getEntryKind() == IClasspathEntry.CPE_PROJECT) {
                Optional<IProject> candidate = findAccessibleJavaProject(cpe.getPath().segment(0));
                if (candidate.isPresent()) {
                    IJavaProject dependencyProject = JavaCore.create(candidate.get());
                    IRuntimeClasspathEntry projectRuntimeEntry = getRuntimeClasspathEntry(dependencyProject, moduleSuppport, "Project", JavaRuntime::newProjectRuntimeClasspathEntry, IJavaProject.class, cpe);
                    // add the project entry itself so that the source lookup can find the classes
                    // see https://github.com/eclipse/buildship/issues/383
                    result.add(projectRuntimeEntry);
                    Collections.addAll(result, invokeJavaRuntimeResolveRuntimeClasspathEntry(projectRuntimeEntry, dependencyProject, excludeTestCode || isTestCodeExcluded(cpe)));
                }
            }
        }
        return result.toArray(new IRuntimeClasspathEntry[result.size()]);
    }

    private <T> IRuntimeClasspathEntry getRuntimeClasspathEntry(final T parameter, boolean moduleSupport, String type, Function<T, IRuntimeClasspathEntry> old, Class<T> parameterType, IClasspathEntry cpe) {
        if (moduleSupport) {
            try {
                Method m = JavaRuntime.class.getMethod(String.format("new%sRuntimeClasspathEntry", type), parameterType, int.class);
                int modulePathAttribute = IRuntimeClasspathEntry.class.getField("MODULE_PATH").getInt(null);
                int classPathAttribute = IRuntimeClasspathEntry.class.getField("CLASS_PATH").getInt(null);
                return (IRuntimeClasspathEntry) m.invoke(null, parameter, hasModuleAttribute(cpe) ? modulePathAttribute : classPathAttribute);
            } catch (Exception e) {
                // Method does not exist yet in this version of eclipse.
                // Revert to old behavior
                return old.apply(parameter);
            }
        } else {
            return old.apply(parameter);
        }
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

    private boolean hasModuleAttribute(IClasspathEntry entry) {
        return hasEnabledBooleanAttribute("module", entry);
    }

    private boolean hasTestAttribute(IClasspathEntry entry) {
        return hasEnabledBooleanAttribute("test", entry);
    }

    private static boolean isTestCodeExcluded(IClasspathEntry entry) {
        return hasEnabledBooleanAttribute("without_test_code", entry);
    }

    private static boolean hasEnabledBooleanAttribute(String key, IClasspathEntry entry) {
        for (IClasspathAttribute attribute : entry.getExtraAttributes()) {
            if (key.equals(attribute.getName()) && Boolean.parseBoolean(attribute.getValue())) {
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
