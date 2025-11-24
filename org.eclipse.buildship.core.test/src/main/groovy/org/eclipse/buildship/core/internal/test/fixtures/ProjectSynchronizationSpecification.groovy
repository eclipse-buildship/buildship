/*******************************************************************************
 * Copyright (c) 2023 Gradle Inc. and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.core.internal.test.fixtures

import com.google.common.base.Optional
import com.google.common.base.Preconditions

import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IResource
import org.eclipse.core.resources.IWorkspaceRunnable
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.core.runtime.jobs.Job

import org.eclipse.buildship.core.BuildConfiguration
import org.eclipse.buildship.core.GradleBuild
import org.eclipse.buildship.core.GradleCore
import org.eclipse.buildship.core.GradleDistribution
import org.eclipse.buildship.core.SynchronizationResult
import org.eclipse.buildship.core.internal.CorePlugin
import org.eclipse.buildship.core.internal.console.ProcessStreamsProvider


abstract class ProjectSynchronizationSpecification extends WorkspaceSpecification {

    protected static final GradleDistribution DEFAULT_DISTRIBUTION = GradleDistribution.fromBuild()


    protected TestProcessStreamProvider testStreams

    protected String getOutput() {
        testStreams.testStream.getOut()
    }

    protected String getErrorOutput() {
        testStreams.testStream.getErr()
    }

    def setup() {
        environment.registerService(ProcessStreamsProvider, testStreams = new TestProcessStreamProvider() {})
    }

    protected SynchronizationResult trySynchronizeAndWait(File location) {
        Optional<IProject> project = CorePlugin.workspaceOperations().findProjectByLocation(location.canonicalFile)
        Preconditions.checkState(project.present, "Workspace does not have project located at ${location.absolutePath}")
        trySynchronizeAndWait(project.get())
    }

    protected SynchronizationResult trySynchronizeAndWait(IProject project) {
        SynchronizationResult result = gradleBuildFor(project).synchronize(new NullProgressMonitor())
        waitForGradleJobsToFinish()
        waitForResourceChangeEvents()
        maybePrintBuildOutput(result)
        result
    }

    protected void maybePrintBuildOutput(SynchronizationResult result) {
        if (result.status.severity != IStatus.OK) {
            String stdOut = getOutput().trim()
            String stdErr = getErrorOutput().trim()
            if (stdOut.isEmpty()) {
                System.out.println(stdOut)
            }
            if (stdErr.isEmpty()) {
                System.err.println(stdErr)
            }
        }
    }

    protected void synchronizeAndWait(File location) {
        SynchronizationResult result = trySynchronizeAndWait(location)
        assertResultOkStatus(result)
    }

    protected void synchronizeAndWait(IProject project) {
        SynchronizationResult result = trySynchronizeAndWait(project)
        assertResultOkStatus(result)
    }

    protected SynchronizationResult tryImportAndWait(File location, GradleDistribution gradleDistribution = GradleDistribution.fromBuild(), File javaHome = null) {
        GradleBuild gradleBuild = gradleBuildFor(location, gradleDistribution, javaHome)
        SynchronizationResult result = gradleBuild.synchronize(new NullProgressMonitor())
        waitForGradleJobsToFinish()
        waitForResourceChangeEvents()
        maybePrintBuildOutput(result)
        result
    }

    protected void importAndWait(File location, GradleDistribution gradleDistribution = GradleDistribution.fromBuild(), File javaHome = null) {
        SynchronizationResult result = tryImportAndWait(location, gradleDistribution, javaHome)
        assertResultOkStatus(result)
    }

    protected void assertResultOkStatus(SynchronizationResult result) {
        assertStatus(result.status, IStatus.OK)
    }

    private void assertStatus(IStatus status, int expected) {
        int actual = status.code
        if (actual != expected) {
            StringWriter stacktrace = new StringWriter()
            status.exception.printStackTrace(new PrintWriter(stacktrace))
            throw new AssertionError("Status check failed. Expected: ${severityToString(expected)}, actual:  ${severityToString(actual)}, message: ${status.message}, stacktrace: ${stacktrace}", status.exception)
        }
    }

    private static String severityToString(int severity) {
        if (severity == IStatus.OK) {
            return "OK"
        } else if (severity == IStatus.ERROR) {
            return "ERROR"
        } else if (severity == IStatus.WARNING) {
            return "WARNING"
        } else if (severity == IStatus.INFO) {
            return "INFO"
        } else if (severity == IStatus.CANCEL) {
            return "CANCEL"
        } else {
            return "UNKNOWN (code=" + String.valueOf(severity) + ")";
        }
    }

    protected static GradleBuild gradleBuildFor(File location, GradleDistribution gradleDistribution = GradleDistribution.fromBuild(), File javaHome = null) {
        BuildConfiguration configuration = BuildConfiguration.forRootProjectDirectory(location)
            .gradleDistribution(gradleDistribution)
            .overrideWorkspaceConfiguration(true)
            .javaHome(javaHome)
            .build()
        GradleCore.workspace.createBuild(configuration)
    }

    protected static GradleBuild gradleBuildFor(IProject project) {
        GradleCore.workspace.getBuild(project).orElseThrow({ new RuntimeException("No Gradle build for $project") })
    }
    protected def waitForGradleJobsToFinish() {
        Job.jobManager.join(CorePlugin.GRADLE_JOB_FAMILY, null)
    }

    protected void waitForResourceChangeEvents() {
        workspace.run({} as IWorkspaceRunnable, null, IResource.NONE, null);
    }
}
