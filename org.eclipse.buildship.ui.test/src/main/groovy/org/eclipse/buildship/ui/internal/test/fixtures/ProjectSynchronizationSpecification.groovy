package org.eclipse.buildship.ui.internal.test.fixtures

import com.google.common.base.Optional
import com.google.common.base.Preconditions

import org.eclipse.core.resources.IProject
import org.eclipse.core.runtime.NullProgressMonitor

import org.eclipse.buildship.core.GradleBuild
import org.eclipse.buildship.core.GradleCore
import org.eclipse.buildship.core.*
import org.eclipse.buildship.core.internal.CorePlugin

abstract class ProjectSynchronizationSpecification extends WorkspaceSpecification {

    protected static final GradleDistribution DEFAULT_DISTRIBUTION = GradleDistributions.fromBuild()

    protected void synchronizeAndWait(File location) {
        Optional<IProject> project = CorePlugin.workspaceOperations().findProjectByLocation(location.canonicalFile)
        Preconditions.checkState(project.present, "Workspace does not have project located at ${location.absolutePath}")
        synchronizeAndWait(project.get())
    }

    protected void synchronizeAndWait(IProject project) {
        GradleCore.workspace.getBuild(project).get().synchronize(new NullProgressMonitor());
        waitForGradleJobsToFinish()
    }

    protected void importAndWait(File location, GradleDistribution gradleDistribution = GradleDistributions.fromBuild()) {
        org.eclipse.buildship.core.configuration.BuildConfiguration configuration = org.eclipse.buildship.core.configuration.BuildConfiguration
             .forRootProjectDirectory(location)
             .gradleDistribution(gradleDistribution)
             .overrideWorkspaceConfiguration(true)
             .build()
        GradleBuild gradleBuild = GradleCore.workspace.createBuild(configuration)
        gradleBuild.synchronize(new NullProgressMonitor())
        waitForGradleJobsToFinish()
        waitForResourceChangeEvents()
    }
}
