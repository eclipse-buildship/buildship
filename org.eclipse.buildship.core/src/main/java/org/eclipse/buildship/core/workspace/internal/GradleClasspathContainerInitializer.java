/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Etienne Studer & Donát Csikós (Gradle Inc.) - initial API and implementation and initial documentation
 */

package org.eclipse.buildship.core.workspace.internal;

import java.util.List;

import org.gradle.tooling.CancellationToken;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProgressListener;
import org.gradle.tooling.events.test.TestProgressListener;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import com.gradleware.tooling.toolingmodel.OmniEclipseGradleBuild;
import com.gradleware.tooling.toolingmodel.OmniEclipseProject;
import com.gradleware.tooling.toolingmodel.OmniExternalDependency;
import com.gradleware.tooling.toolingmodel.repository.FetchStrategy;
import com.gradleware.tooling.toolingmodel.repository.FixedRequestAttributes;
import com.gradleware.tooling.toolingmodel.repository.ModelRepository;
import com.gradleware.tooling.toolingmodel.repository.TransientRequestAttributes;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.ClasspathContainerInitializer;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.buildship.core.CorePlugin;
import org.eclipse.buildship.core.GradlePluginsRuntimeException;
import org.eclipse.buildship.core.configuration.ProjectConfiguration;
import org.eclipse.buildship.core.console.ProcessStreams;
import org.eclipse.buildship.core.gradle.Specs;
import org.eclipse.buildship.core.i18n.CoreMessages;
import org.eclipse.buildship.core.workspace.ClasspathDefinition;

/**
 * Initializes the classpath of each Eclipse workspace project that has a Gradle nature with the
 * external dependencies of the underlying Gradle project.
 * <p/>
 * When this initializer is invoked, it looks up the {@link OmniEclipseProject} for the given
 * Eclipse workspace project, takes all the found external Jar dependencies, and assigns them to the
 * {@link ClasspathDefinition#GRADLE_CLASSPATH_CONTAINER_ID} classpath container.
 * <p/>
 * This initializer is assigned to the projects via the
 * {@code org.eclipse.jdt.core.classpathContainerInitializer} extension point.
 * <p/>
 * The initialization is scheduled as a job, to not block the IDE upon startup.
 */
public final class GradleClasspathContainerInitializer extends ClasspathContainerInitializer {

    /**
     * Looks up the {@link OmniEclipseProject} for the target project, takes all external Jar
     * dependencies and assigns them to the classpath container with id
     * {@link ClasspathDefinition#GRADLE_CLASSPATH_CONTAINER_ID}.
     */
    @Override
    public void initialize(final IPath containerPath, final IJavaProject project) throws CoreException {
        new Job(CoreMessages.GradleClasspathContainerInitializer_InitializeClassPath + project.getElementName() + "'") { //$NON-NLS-1$

            // todo (etst) review job creation
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    internalInitialize(containerPath, project);
                    return Status.OK_STATUS;
                } catch (Exception e) {
                    // TODO (donat) add a marker describing the problem
                    String message = String.format(CoreMessages.GradleClasspathContainerInitializer_ErrorMessage_FailToInitializeClassPath, project.getProject());
                    CorePlugin.logger().error(message, e);
                    return new Status(IStatus.ERROR, CorePlugin.PLUGIN_ID, message, e);
                }
            }
        }.schedule();
    }

    private void internalInitialize(IPath containerPath, IJavaProject project) throws JavaModelException {
        Optional<OmniEclipseProject> eclipseProject = findEclipseProject(project.getProject());
        if (eclipseProject.isPresent()) {
            ImmutableList<IClasspathEntry> externalDependencies = collectExternalDependencies(eclipseProject.get());
            setClasspathContainer(externalDependencies, containerPath, project);
            project.save(new NullProgressMonitor(), true);
        } else {
            throw new GradlePluginsRuntimeException(String.format(CoreMessages.GradleClasspathContainerInitializer_ErrorMessage_CanNotFindEclipseProjectModel, project.getProject()));
        }
    }

    private Optional<OmniEclipseProject> findEclipseProject(IProject project) {
        ProjectConfiguration configuration = CorePlugin.projectConfigurationManager().readProjectConfiguration(project);
        OmniEclipseGradleBuild eclipseGradleBuild = fetchEclipseGradleBuild(configuration.getRequestAttributes());
        return eclipseGradleBuild.getRootEclipseProject().tryFind(Specs.eclipseProjectMatchesProjectPath(configuration.getProjectPath()));
    }

    private OmniEclipseGradleBuild fetchEclipseGradleBuild(FixedRequestAttributes fixedRequestAttributes) {
        ProcessStreams streams = CorePlugin.processStreamsProvider().getBackgroundJobProcessStreams();
        List<ProgressListener> noProgressListeners = ImmutableList.of();
        List<TestProgressListener> noTestProgressListeners = ImmutableList.of();
        CancellationToken cancellationToken = GradleConnector.newCancellationTokenSource().token();
        TransientRequestAttributes transientAttributes = new TransientRequestAttributes(false, streams.getOutput(), streams.getError(), null, noProgressListeners,
                noTestProgressListeners, cancellationToken);
        ModelRepository repository = CorePlugin.modelRepositoryProvider().getModelRepository(fixedRequestAttributes);
        return repository.fetchEclipseGradleBuild(transientAttributes, FetchStrategy.LOAD_IF_NOT_CACHED);
    }

    private ImmutableList<IClasspathEntry> collectExternalDependencies(OmniEclipseProject project) {
        return FluentIterable.from(project.getExternalDependencies()).transform(new Function<OmniExternalDependency, IClasspathEntry>() {

            @Override
            public IClasspathEntry apply(OmniExternalDependency dependency) {
                IPath jar = org.eclipse.core.runtime.Path.fromOSString(dependency.getFile().getAbsolutePath());
                IPath sourceJar = dependency.getSource() != null ? org.eclipse.core.runtime.Path.fromOSString(dependency.getSource().getAbsolutePath()) : null;
                return JavaCore.newLibraryEntry(jar, sourceJar, null, true);
            }
        }).toList();
    }

    private void setClasspathContainer(List<IClasspathEntry> classpathEntries, IPath containerPath, IJavaProject project) throws JavaModelException {
        org.eclipse.core.runtime.Path classpathContainerPath = new org.eclipse.core.runtime.Path(ClasspathDefinition.GRADLE_CLASSPATH_CONTAINER_ID);
        IClasspathContainer classpathContainer = new ExternalDependenciesClasspathContainer(classpathContainerPath, classpathEntries);
        JavaCore.setClasspathContainer(containerPath, new IJavaProject[] { project }, new IClasspathContainer[] { classpathContainer }, null);
    }

    /**
     * {@code IClasspathContainer} to describe the external dependencies.
     */
    private static final class ExternalDependenciesClasspathContainer implements IClasspathContainer {

        private final org.eclipse.core.runtime.Path path;
        private final IClasspathEntry[] classpathEntries;

        private ExternalDependenciesClasspathContainer(org.eclipse.core.runtime.Path path, List<IClasspathEntry> classpathEntries) {
            this.path = path;
            this.classpathEntries = Iterables.toArray(classpathEntries, IClasspathEntry.class);
        }

        @Override
        public IPath getPath() {
            return this.path;
        }

        @Override
        public IClasspathEntry[] getClasspathEntries() {
            return this.classpathEntries;
        }

        @Override
        public int getKind() {
            return IClasspathContainer.K_APPLICATION;
        }

        @Override
        public String getDescription() {
            return CoreMessages.GradleClasspathContainerInitializer_Description_ExternalDependencies;
        }

    }
}
