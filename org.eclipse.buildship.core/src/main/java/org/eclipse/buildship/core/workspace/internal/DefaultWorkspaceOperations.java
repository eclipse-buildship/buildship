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

import java.io.File;
import java.util.List;

import com.google.common.base.Function;
import com.google.common.base.MoreObjects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.buildship.core.CorePlugin;
import org.eclipse.buildship.core.GradlePluginsRuntimeException;
import org.eclipse.buildship.core.i18n.CoreMessages;
import org.eclipse.buildship.core.workspace.ClasspathDefinition;
import org.eclipse.buildship.core.workspace.WorkspaceOperations;

/**
 * Default implementation of the {@link WorkspaceOperations} interface.
 */
public final class DefaultWorkspaceOperations implements WorkspaceOperations {

    @Override
    public ImmutableList<IProject> getAllProjects() {
        return ImmutableList.copyOf(ResourcesPlugin.getWorkspace().getRoot().getProjects());
    }

    @Override
    public Optional<IProject> findProjectByName(final String name) {
        return FluentIterable.from(getAllProjects()).firstMatch(new Predicate<IProject>() {

            @Override
            public boolean apply(IProject project) {
                return project.getName().equals(name);
            }
        });
    }

    @Override
    public void deleteAllProjects(IProgressMonitor monitor) {
        monitor = MoreObjects.firstNonNull(monitor, new NullProgressMonitor());
        monitor.beginTask(CoreMessages.DefaultWorkspaceOperations_DeleteAllEclipseProjectsFromWorkspace, 100);
        try {
            List<IProject> allProjects = getAllProjects();
            for (IProject project : allProjects) {
                try {
                    project.delete(true, new SubProgressMonitor(monitor, 100 / allProjects.size()));
                } catch (Exception e) {
                    String message = String.format(CoreMessages.DefaultWorkspaceOperations_ErrorMessage_CanNotDeleteProject, project);
                    CorePlugin.logger().error(message, e);
                    throw new GradlePluginsRuntimeException(message, e);
                }
            }
        } finally {
            monitor.done();
        }
    }

    @Override
    public IProject createProject(String name, File location, List<File> childProjectLocations, List<String> natureIds, IProgressMonitor monitor) {
        // validate arguments
        Preconditions.checkNotNull(name);
        Preconditions.checkNotNull(location);
        Preconditions.checkNotNull(natureIds);
        Preconditions.checkArgument(!name.isEmpty(), CoreMessages.DefaultWorkspaceOperations_ProjectNameMustNotBeEmpty);
        Preconditions.checkArgument(location.exists(), String.format(CoreMessages.DefaultWorkspaceOperations_ProjectLocationMustExist, location));
        Preconditions.checkArgument(location.isDirectory(), String.format(CoreMessages.DefaultWorkspaceOperations_ProjectLocationMustBeADirectory, location));

        monitor = MoreObjects.firstNonNull(monitor, new NullProgressMonitor());
        monitor.beginTask(String.format(CoreMessages.DefaultWorkspaceOperations_CreateEclipseProject, name), 4 + natureIds.size());
        try {
            // make sure no project with the specified name already exists
            Preconditions.checkState(!findProjectByName(name).isPresent(), String.format(CoreMessages.DefaultWorkspaceOperations_WorkspaceAlreadyContainsProjectName, name));
            monitor.worked(1);

            // get an IProject instance and create the project
            IWorkspace workspace = ResourcesPlugin.getWorkspace();
            IProjectDescription desc = workspace.newProjectDescription(name);
            desc.setLocation(Path.fromOSString(location.getPath()));
            IProject project = workspace.getRoot().getProject(name);
            project.create(desc, new SubProgressMonitor(monitor, 1));

            // attach filters to the project to hide the sub-projects of this project
            ResourceFilter.attachFilters(project, childProjectLocations, new SubProgressMonitor(monitor, 1));

            // open the project
            project.open(new SubProgressMonitor(monitor, 1));

            // add project natures separately to trigger IProjectNature#configure
            // the project needs to be open while the natures are added
            for (String natureId : natureIds) {
                addNature(project, natureId, new SubProgressMonitor(monitor, 1));
            }

            // return the created, open project
            return project;
        } catch (Exception e) {
            String message = String.format(CoreMessages.DefaultWorkspaceOperations_ErrorMessage_CanNotCreateEclipseProject, name);
            CorePlugin.logger().error(message, e);
            throw new GradlePluginsRuntimeException(message, e);
        } finally {
            monitor.done();
        }
    }

    @Override
    public IJavaProject createJavaProject(IProject project, ClasspathDefinition classpath, IProgressMonitor monitor) {
        // validate arguments
        Preconditions.checkNotNull(project);
        Preconditions.checkNotNull(classpath);
        Preconditions.checkArgument(project.isAccessible(), CoreMessages.DefaultWorkspaceOperations_ProjectMustBeOpen);

        monitor = MoreObjects.firstNonNull(monitor, new NullProgressMonitor());
        monitor.beginTask(String.format(CoreMessages.DefaultWorkspaceOperations_CreateEclipseJavaProject, project.getName()), 17);
        try {
            // add Java nature
            addNature(project, JavaCore.NATURE_ID, new SubProgressMonitor(monitor, 2));

            // create the Eclipse Java project from the plain Eclipse project
            IJavaProject javaProject = JavaCore.create(project);
            monitor.worked(5);

            // set up resources (sources and classpath)
            setSourcesAndClasspathOnProject(javaProject, classpath, new SubProgressMonitor(monitor, 5));

            // set up output location
            IFolder outputFolder = createOutputFolder(project, new SubProgressMonitor(monitor, 1));
            javaProject.setOutputLocation(outputFolder.getFullPath(), new SubProgressMonitor(monitor, 1));

            // avoid out-of-sync messages when the content of the .gradle folder changes upon running a Gradle build
            markDotGradleFolderAsDerived(project, new SubProgressMonitor(monitor, 1));

            // save the project configuration
            javaProject.save(new SubProgressMonitor(monitor, 2), true);

            // return the created Java project
            return javaProject;
        } catch (Exception e) {
            String message = String.format(CoreMessages.DefaultWorkspaceOperations_ErrorMessage_CanNotCreateEclipseJavaProject, project.getName());
            CorePlugin.logger().error(message, e);
            throw new GradlePluginsRuntimeException(message, e);
        } finally {
            monitor.done();
        }
    }

    private void addNature(IProject project, String natureId, IProgressMonitor monitor) {
        monitor.beginTask(String.format(CoreMessages.DefaultWorkspaceOperations_AddNatureToEclipseProject, natureId, project.getName()), 1);
        try {
            // get the description
            IProjectDescription description = project.getDescription();

            // abort if the project already has the nature applied
            List<String> currentNatureIds = ImmutableList.copyOf(description.getNatureIds());
            if (currentNatureIds.contains(natureId)) {
                return;
            }

            // add the nature to the project
            ImmutableList<String> newIds = ImmutableList.<String> builder().addAll(currentNatureIds).add(natureId).build();
            description.setNatureIds(newIds.toArray(new String[newIds.size()]));

            // save the updated description
            project.setDescription(description, new SubProgressMonitor(monitor, 1));
        } catch (CoreException e) {
            String message = String.format(CoreMessages.DefaultWorkspaceOperations_ErrorMessage_CanNotAddNatureToEclipseProject, natureId, project.getName());
            CorePlugin.logger().error(message, e);
            throw new GradlePluginsRuntimeException(message, e);
        } finally {
            monitor.done();
        }
    }

    private IFolder createOutputFolder(IProject project, IProgressMonitor monitor) {
        monitor.beginTask(String.format(CoreMessages.DefaultWorkspaceOperations_CreateOutputFolderForEclipseProject, project.getName()), 1);
        try {
            IFolder outputFolder = project.getFolder("bin"); //$NON-NLS-1$
            if (!outputFolder.exists()) {
                outputFolder.create(true, true, new SubProgressMonitor(monitor, 1));
            }
            return outputFolder;
        } catch (Exception e) {
            String message = String.format(CoreMessages.DefaultWorkspaceOperations_ErrorMessage_CanNotCreateOutputFolderForEclipse, project.getName());
            CorePlugin.logger().error(message, e);
            throw new GradlePluginsRuntimeException(message, e);
        } finally {
            monitor.done();
        }
    }

    private void markDotGradleFolderAsDerived(IProject project, IProgressMonitor monitor) throws CoreException {
        monitor.beginTask(String.format(CoreMessages.DefaultWorkspaceOperations_MarkFolderasDerivedForEclipseProject, project.getName()), 1);
        try {
            IFolder dotGradleFolder = project.getFolder(".gradle"); //$NON-NLS-1$
            if (dotGradleFolder.exists()) {
                dotGradleFolder.setDerived(true, new SubProgressMonitor(monitor, 1));
            }
        } finally {
            monitor.done();
        }
    }

    private void setSourcesAndClasspathOnProject(IJavaProject javaProject, ClasspathDefinition classpath, IProgressMonitor monitor) {
        monitor.beginTask(String.format(CoreMessages.DefaultWorkspaceOperations_ConfigSourcesAndClasspathForEclipse, javaProject.getProject().getName()), 12);
        try {
            // create a new holder for all classpath entries
            Builder<IClasspathEntry> entries = ImmutableList.builder();

            // add the library with the JRE dependencies
            entries.add(JavaCore.newContainerEntry(classpath.getJrePath()));
            monitor.worked(1);

            // add classpath definition of where to store the external dependencies, the classpath
            // will be populated lazily by the org.eclipse.jdt.core.classpathContainerInitializer
            // extension point (see GradleClasspathContainerInitializer)
            entries.add(createClasspathContainerForExternalDependencies());
            monitor.worked(1);

            // add project dependencies
            entries.addAll(collectProjectDependencies(classpath));
            monitor.worked(1);

            // add source directories; create the directory if it doesn't exist
            entries.addAll(collectSourceDirectories(classpath, javaProject));
            monitor.worked(1);

            // assign the whole classpath at once to the project
            List<IClasspathEntry> entriesArray = entries.build();
            javaProject.setRawClasspath(entriesArray.toArray(new IClasspathEntry[entriesArray.size()]), new SubProgressMonitor(monitor, 8));
        } catch (Exception e) {
            String message = String.format(CoreMessages.DefaultWorkspaceOperations_ErrorMessage_CanNotConfigSourcesAndClassPathForEclispeProject, javaProject.getProject().getName());
            CorePlugin.logger().error(message, e);
            throw new GradlePluginsRuntimeException(message, e);
        } finally {
            monitor.done();
        }
    }

    private IClasspathEntry createClasspathContainerForExternalDependencies() throws JavaModelException {
        // http://www-01.ibm.com/support/knowledgecenter/SSZND2_6.0.0/org.eclipse.jdt.doc.isv/guide/jdt_api_classpath.htm?cp=SSZND2_6.0.0%2F3-1-1-0-0-2
        Path containerPath = new Path(ClasspathDefinition.GRADLE_CLASSPATH_CONTAINER_ID);
        return JavaCore.newContainerEntry(containerPath, true);
    }

    private List<IClasspathEntry> collectProjectDependencies(ClasspathDefinition classpath) {
        return FluentIterable.from(classpath.getProjectDependencies()).transform(new Function<IPath, IClasspathEntry>() {

            @Override
            public IClasspathEntry apply(IPath dependency) {
                return JavaCore.newProjectEntry(dependency, true);
            }
        }).toList();
    }

    private List<IClasspathEntry> collectSourceDirectories(ClasspathDefinition classpath, final IJavaProject javaProject) {
        return FluentIterable.from(classpath.getSourceDirectories()).transform(new Function<String, IClasspathEntry>() {

            @Override
            public IClasspathEntry apply(String directory) {
                IFolder sourceDirectory = javaProject.getProject().getFolder(Path.fromOSString(directory));
                ensureFolderHierarchyExists(sourceDirectory);
                IPackageFragmentRoot root = javaProject.getPackageFragmentRoot(sourceDirectory);
                return JavaCore.newSourceEntry(root.getPath());
            }
        }).toList();
    }

    private void ensureFolderHierarchyExists(IFolder folder) {
        if (!folder.exists()) {
            if (folder.getParent() instanceof IFolder) {
                ensureFolderHierarchyExists((IFolder) folder.getParent());
            }

            try {
                folder.create(true, true, null);
            } catch (CoreException e) {
                String message = String.format(CoreMessages.DefaultWorkspaceOperations_ErrorMessage_CanNotCreateFolder, folder);
                CorePlugin.logger().error(message, e);
                throw new GradlePluginsRuntimeException(message, e);
            }
        }
    }

    @Override
    public void refresh(IProject project, IProgressMonitor monitor) {
        // validate arguments
        Preconditions.checkNotNull(project);
        Preconditions.checkArgument(project.isAccessible(), CoreMessages.DefaultWorkspaceOperations_ProjectMustBeOpen);

        monitor = MoreObjects.firstNonNull(monitor, new NullProgressMonitor());
        monitor.beginTask(String.format(CoreMessages.DefaultWorkspaceOperations_RefreshEclipseProject, project.getName()), 1);
        try {
            project.refreshLocal(IProject.DEPTH_INFINITE, new SubProgressMonitor(monitor, 1));
        } catch (Exception e) {
            String message = String.format(CoreMessages.DefaultWorkspaceOperations_ErrorMessage_CanNotRefreshEclipseProject, project.getName());
            CorePlugin.logger().error(message, e);
            throw new GradlePluginsRuntimeException(message, e);
        } finally {
            monitor.done();
        }
    }

}
