/*******************************************************************************
 * Copyright (c) 2023 Gradle Inc. and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.core.internal.launch

import org.eclipse.debug.core.ILaunchConfiguration

import org.eclipse.buildship.core.GradleDistribution
import org.eclipse.buildship.core.internal.CorePlugin
import org.eclipse.buildship.core.internal.console.ProcessStreamsProvider
import org.eclipse.buildship.core.internal.test.fixtures.ProjectSynchronizationSpecification
import org.eclipse.buildship.core.internal.test.fixtures.TestProcessStreamProvider
import org.eclipse.buildship.core.internal.test.fixtures.WorkspaceSpecification

class BaseLaunchRequestJobTest extends ProjectSynchronizationSpecification {

    String getBuildOutput() {
        TestProcessStreamProvider testStreams = CorePlugin.processStreamsProvider()
        testStreams.testStream.out
    }

    String getBuildConfig() {
        TestProcessStreamProvider testStreams = CorePlugin.processStreamsProvider()
        testStreams.testStream.conf
    }

    ILaunchConfiguration createLaunchConfiguration(File projectDir, tasks = ['clean', 'build'], GradleDistribution distribution = GradleDistribution.fromBuild(), arguments = []) {
        ILaunchConfiguration launchConfiguration = Mock(ILaunchConfiguration)
        launchConfiguration.getName() >> 'name'
        launchConfiguration.getAttribute('override_workspace_settings', _) >> 'true'
        launchConfiguration.getAttribute('tasks', _) >> tasks
        launchConfiguration.getAttribute('working_dir', _) >> projectDir
        launchConfiguration.getAttribute('gradle_distribution', _) >> distribution.toString()
        launchConfiguration.getAttribute('arguments', _) >> arguments
        launchConfiguration.getAttribute('jvm_arguments', _) >> []
        launchConfiguration
    }

}
