/*******************************************************************************
 * Copyright (c) 2019 Gradle Inc.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.core.internal.workspace;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.eclipse.EclipseLinkedResource;
import org.gradle.tooling.model.eclipse.EclipseProject;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubMonitor;

import org.eclipse.buildship.core.internal.GradlePluginsRuntimeException;
import org.eclipse.buildship.core.internal.preferences.PersistentModel;
import org.eclipse.buildship.core.internal.util.file.RelativePathUtils;
import org.eclipse.buildship.core.internal.util.gradle.HierarchicalElementUtils;

/**
 * Updates the derived resource markers on a project. Stores the last state in the preferences, so
 * we can remove the derived markers later.
 *
 * @author Stefan Oehme
 */
final class GradleFolderUpdater {

    private static final String DEFAULT_BUILD_DIR_NAME = "build";

    private final IProject workspaceProject;
    private final EclipseProject modelProject;

    private GradleFolderUpdater(IProject workspaceProject, EclipseProject modelProject) {
        this.workspaceProject = Preconditions.checkNotNull(workspaceProject);
        this.modelProject = Preconditions.checkNotNull(modelProject);
    }

    private void update(PersistentModelBuilder persistentModel, IProgressMonitor monitor) {
        SubMonitor progress = SubMonitor.convert(monitor, 3);
        try {
            GradleFolderInfo folderInfo = collectFolderInfo(progress.newChild(1));
            Collection<IPath> folderPaths = folderInfo.toPathList();
            persistentModel.buildDir(folderInfo.getProjectBuildDir());
            persistentModel.subprojectPaths(folderInfo.getNestedProjectPaths());
            removePreviousMarkers(folderPaths, persistentModel, progress.newChild(1));
            addNewMarkers(folderPaths, persistentModel, progress.newChild(1));
        } catch (CoreException e) {
            String message = String.format("Could not update folder information on project %s.", this.workspaceProject.getName());
            throw new GradlePluginsRuntimeException(message, e);
        } finally {
            if (monitor != null) {
                monitor.done();
            }
        }
    }

    private GradleFolderInfo collectFolderInfo(IProgressMonitor monitor) {
        IPath currentProjectPath = this.workspaceProject.getLocation();

        IPath currentProjectBuildDirPath = new Path(DEFAULT_BUILD_DIR_NAME);
        List<IPath> nestedProjectPaths = Lists.newArrayList();
        List<IPath> nestedBuildDirPaths = Lists.newArrayList();


        for (EclipseProject project : HierarchicalElementUtils.getAll(this.modelProject)) {
            GradleProject gradleProject = project.getGradleProject();
            IPath projectPath = Path.fromOSString(project.getProjectDirectory().getPath());
            if (currentProjectPath.isPrefixOf(projectPath)) {
                IPath relativePath = RelativePathUtils.getRelativePath(currentProjectPath, projectPath);
                IPath buildDirPath = getBuildDirectoryPath(gradleProject.getBuildDirectory(), relativePath);
                if (relativePath.segmentCount() == 0) {
                    currentProjectBuildDirPath = buildDirPath;
                } else {
                    nestedProjectPaths.add(relativePath);
                    if (buildDirPath != null) {
                        nestedBuildDirPaths.add(buildDirPath);
                    }
                }
            }
        }

        return new GradleFolderInfo(currentProjectBuildDirPath, nestedProjectPaths, nestedBuildDirPaths);
    }

    private void removePreviousMarkers(Collection<IPath> folderPaths, PersistentModelBuilder persistentModel, SubMonitor progress) throws CoreException {
        PersistentModel previousModel = persistentModel.getPrevious();
        Collection<IPath> previouslyKnownPaths = previousModel.isPresent() ? previousModel.getDerivedResources() : Collections.<IPath>emptyList();
        progress.setWorkRemaining(previouslyKnownPaths.size());
        for (IPath resourcePath : previouslyKnownPaths) {
            IResource resource = this.workspaceProject.findMember(resourcePath);
            if (resource != null) {
                resource.setDerived(false, progress.newChild(1));
            } else {
                progress.worked(1);
            }
        }
    }

    private void addNewMarkers(Collection<IPath> folderPaths, PersistentModelBuilder persistentModel, SubMonitor progress) throws CoreException {
        progress.setWorkRemaining(folderPaths.size());
        for (IPath resourcePath : folderPaths) {
            IResource resource = this.workspaceProject.findMember(resourcePath);
            if (resource != null) {
                resource.setDerived(true, progress.newChild(1));
            } else {
                progress.worked(1);
            }
        }
        persistentModel.derivedResources(folderPaths);
    }

    /*
     * If no build directory is available via the TAPI, use 'build'. If build directory is
     * physically contained in the project, use that folder. If build directory is a linked
     * resource, use the linked folder. Optional.absent() if all of the above fail.
     */
    private IPath getBuildDirectoryPath(File file, IPath prefix) {
        IPath buildDirPath = null;
        if (file != null) {
            buildDirPath = normalizeBuildDirectoryPath(new Path(file.getPath()));
        }
        return buildDirPath != null ? buildDirPath : prefix.append(DEFAULT_BUILD_DIR_NAME);
    }

    private IPath normalizeBuildDirectoryPath(IPath buildDirLocation) {
        IPath projectLocation = this.workspaceProject.getLocation();
        if (projectLocation.isPrefixOf(buildDirLocation)) {
            IPath relativePath = RelativePathUtils.getRelativePath(projectLocation, buildDirLocation);
            return relativePath;
        } else {
            for (EclipseLinkedResource linkedResource : this.modelProject.getLinkedResources()) {
                if (buildDirLocation.toString().equals(linkedResource.getLocation())) {
                    return new Path(linkedResource.getName());
                }
            }
            // TODO (donat) if an external build folder is not added as a linked resource than we should treat it as absent and not use the default 'build' folder
            return null;
        }
    }

    static void update(IProject workspaceProject, EclipseProject project, PersistentModelBuilder persistentModel, IProgressMonitor monitor) {
        new GradleFolderUpdater(workspaceProject, project).update(persistentModel, monitor);
    }

    /**
     * Helper class to hold Gradle-specific folders.
     */
    private static final class GradleFolderInfo {

        private final IPath projectBuildDir;
        private final Collection<IPath> nestedProjectPaths;
        private final Collection<IPath> nestedProjectBuildDirs;

        public GradleFolderInfo(IPath projectBuildDir, Collection<IPath> nestedProjectPaths, Collection<IPath> nestedProjectBuildDirs) {
            this.projectBuildDir = projectBuildDir;
            this.nestedProjectPaths = nestedProjectPaths;
            this.nestedProjectBuildDirs = nestedProjectBuildDirs;
        }

        public IPath getProjectBuildDir() {
            return this.projectBuildDir;
        }

        public Collection<IPath> getNestedProjectPaths() {
            return this.nestedProjectPaths;
        }

        public Collection<IPath> toPathList() {
            Set<IPath> result = Sets.newLinkedHashSet();
            result.add(new Path(".gradle"));
            if (this.projectBuildDir != null) {
                result.add(this.projectBuildDir);
            }
            result.addAll(this.nestedProjectBuildDirs);
            return result;
        }
    }
}
