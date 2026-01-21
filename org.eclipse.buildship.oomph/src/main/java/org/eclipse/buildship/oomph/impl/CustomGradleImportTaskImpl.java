/*******************************************************************************
 * Copyright (c) 2023 Gradle Inc. and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.oomph.impl;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.emf.common.util.EList;
import org.eclipse.oomph.resources.SourceLocator;
import org.eclipse.oomph.setup.SetupTaskContext;
import org.eclipse.oomph.setup.Trigger;
import org.eclipse.osgi.util.NLS;

import org.eclipse.buildship.core.BuildConfiguration;
import org.eclipse.buildship.core.GradleBuild;
import org.eclipse.buildship.core.GradleCore;
import org.eclipse.buildship.core.GradleDistribution;
import org.eclipse.buildship.core.GradleWorkspace;
import org.eclipse.buildship.core.BuildConfiguration.BuildConfigurationBuilder;
import org.eclipse.buildship.oomph.ImportTaskMessages;

public class CustomGradleImportTaskImpl extends GradleImportTaskImpl {

    private static final IWorkspaceRoot ROOT = ResourcesPlugin.getWorkspace().getRoot();

    @Override
    public boolean isNeeded(SetupTaskContext context) throws Exception {
        EList<SourceLocator> sourceLocators = getSourceLocators();
        if (sourceLocators.isEmpty()) {
            return false;
        }
        return true;
    }

    @Override
    public void perform(SetupTaskContext context) throws Exception {
        List<SourceLocator> sourceLocators = getSourceLocators();
        if (context.getTrigger() != Trigger.MANUAL) {
            // Only import new projects on startup triggers
            sourceLocators = calculateProjectsToImport();
        }
        SubMonitor monitor = SubMonitor.convert(context.getProgressMonitor(true));
        monitor.beginTask("", sourceLocators.size()); //$NON-NLS-1$
        try {
            GradleWorkspace workspace = GradleCore.getWorkspace();
            for (SourceLocator sourceLocator : sourceLocators) {
                context.log(NLS.bind(ImportTaskMessages.GradleImportTaskImpl_importing, sourceLocator.getRootFolder()));
                SubMonitor progress = monitor.newChild(1);
                Optional<GradleBuild> gradleBuildHolder = Optional.empty();

                for (IProject project : ROOT.getProjects()) {
                    if (!gradleBuildHolder.isPresent() && project.getLocation().equals(new Path(sourceLocator.getRootFolder()))) {
                        gradleBuildHolder = workspace.getBuild(project);
                        context.log(NLS.bind(ImportTaskMessages.GradleImportTaskImpl_found_existing, project.getName()));
                    }
                }
                GradleBuild gradleBuild = gradleBuildHolder.orElseGet(new GradleBuildSupplier(context, new File(sourceLocator.getRootFolder())));
                progress.setWorkRemaining(1);

                gradleBuild.synchronize(progress.newChild(1));
            }
        } finally {
            monitor.done();
        }
    }

    private List<SourceLocator> calculateProjectsToImport() {
        List<SourceLocator> projectsToImport = new ArrayList<>();
        for (SourceLocator sourceLocator : this.sourceLocators) {
            Path rootFolder = new Path(sourceLocator.getRootFolder());
            boolean projectPresentInWorkspace = false;
            for (IProject project : ROOT.getProjects()) {
                IPath projectFolder = project.getLocation();
                try {
                    if (Files.isSameFile(java.nio.file.Paths.get(projectFolder.toOSString()), java.nio.file.Paths.get(rootFolder.toOSString()))) {
                        projectPresentInWorkspace = true;
                        break;
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to compare project paths for equality", e);
                }
            }
            if (!projectPresentInWorkspace) {
                projectsToImport.add(sourceLocator);
            }
        }
        return projectsToImport;
    }

    private class GradleBuildSupplier implements Supplier<GradleBuild> {

        private final SetupTaskContext context;
        private final File rootFolder;

        public GradleBuildSupplier(SetupTaskContext context, File rootFolder) {
            this.context = context;
            this.rootFolder = rootFolder;
        }

        @Override
        public GradleBuild get() {
            GradleWorkspace workspace = GradleCore.getWorkspace();
            BuildConfigurationBuilder configBuilder = BuildConfiguration.forRootProjectDirectory(this.rootFolder);
            if (isOverrideWorkspaceSettings()) {
                configBuilder.overrideWorkspaceConfiguration(true);
                switch (getDistributionType()) {
                    case GRADLE_WRAPPER:
                        configBuilder.gradleDistribution(GradleDistribution.fromBuild());
                        break;
                    case LOCAL_INSTALLATION:
                        configBuilder.gradleDistribution(GradleDistribution.forLocalInstallation(new File(getLocalInstallationDirectory())));
                        break;
                    case REMOTE_DISTRIBUTION:
                        try {
                            configBuilder.gradleDistribution(GradleDistribution.forRemoteDistribution(new URI(getRemoteDistributionLocation())));
                        } catch (URISyntaxException e) {
                            throw new IllegalArgumentException("Invalid Gradle distribution uri " + getRemoteDistributionLocation(), e);
                        }
                        break;
                    case SPECIFIC_GRADLE_VERSION:
                        configBuilder.gradleDistribution(GradleDistribution.forVersion(getSpecificGradleVersion()));
                        break;
                    default:
                        throw new IllegalArgumentException("Unsupported distributionType " + getDistributionType());

                }
                if (getGradleUserHome() != null) {
                    configBuilder.gradleUserHome(new File(getGradleUserHome()));
                }
                if (getJavaHome() != null) {
                    configBuilder.javaHome(new File(getJavaHome()));
                }
                configBuilder.arguments(CustomGradleImportTaskImpl.this.programArguments).jvmArguments(getJvmArguments()).offlineMode(isOfflineMode()).buildScansEnabled(isBuildScans())
                        .autoSync(isAutomaticProjectSynchronization()).showConsoleView(isShowConsoleView()).showExecutionsView(isShowExecutionsView());

            }
            BuildConfiguration configuration = configBuilder.build();
            this.context.log(ImportTaskMessages.GradleImportTaskImpl_import_new);
            return workspace.createBuild(configuration);
        }

    }

}
