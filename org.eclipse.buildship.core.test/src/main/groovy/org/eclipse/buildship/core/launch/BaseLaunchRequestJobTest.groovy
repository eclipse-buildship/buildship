package org.eclipse.buildship.core.launch

import org.eclipse.debug.core.ILaunchConfiguration

import org.eclipse.buildship.core.GradleDistribution
import org.eclipse.buildship.core.console.ProcessStreams
import org.eclipse.buildship.core.console.ProcessStreamsProvider
import org.eclipse.buildship.core.test.fixtures.WorkspaceSpecification

class BaseLaunchRequestJobTest extends WorkspaceSpecification {

    private ProcessStreamsProvider processStreamsProvider
    private ByteArrayOutputStream buildOutputStream
    private ByteArrayOutputStream buildConfigurationStream

    def setup() {
        ProcessStreams processStreams = Mock(ProcessStreams)
        buildOutputStream = new ByteArrayOutputStream()
        buildConfigurationStream = new ByteArrayOutputStream()
        processStreams.output >> buildOutputStream
        processStreams.configuration >> buildConfigurationStream

        processStreamsProvider = Mock(ProcessStreamsProvider)
        processStreamsProvider.createProcessStreams(_) >> processStreams
        processStreamsProvider.getBackgroundJobProcessStreams() >> processStreams

        environment.registerService(ProcessStreamsProvider, processStreamsProvider)
    }

    String getBuildOutput() {
        buildOutputStream.toString()
    }

    String getBuildConfig() {
        buildConfigurationStream.toString()
    }

    ILaunchConfiguration createLaunchConfiguration(File projectDir, tasks = ['clean', 'build'], GradleDistribution distribution = GradleDistribution.fromBuild(), arguments = []) {
        ILaunchConfiguration launchConfiguration = Mock(ILaunchConfiguration)
        launchConfiguration.getName() >> 'name'
        launchConfiguration.getAttribute('override_workspace_settings', _) >> 'true'
        launchConfiguration.getAttribute('tasks', _) >> tasks
        launchConfiguration.getAttribute('working_dir', _) >> projectDir
        launchConfiguration.getAttribute('gradle_distribution', _) >> distribution.distributionInfo.serializeToString()
        launchConfiguration.getAttribute('arguments', _) >> arguments
        launchConfiguration.getAttribute('jvm_arguments', _) >> []
        launchConfiguration
    }

}
