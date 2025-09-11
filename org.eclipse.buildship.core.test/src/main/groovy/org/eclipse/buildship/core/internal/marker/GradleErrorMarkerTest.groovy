/*******************************************************************************
 * Copyright (c) 2023 Gradle Inc. and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.core.internal.marker

import org.gradle.tooling.BuildException

import org.eclipse.core.resources.IMarker
import org.eclipse.core.resources.IResource
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.IStatus

import org.eclipse.buildship.core.GradleDistribution
import org.eclipse.buildship.core.internal.CorePlugin
import org.eclipse.buildship.core.internal.configuration.WorkspaceConfiguration
import org.eclipse.buildship.core.internal.test.fixtures.ProjectSynchronizationSpecification

class GradleErrorMarkerTest extends ProjectSynchronizationSpecification {

    def setupSpec() {
        // delete all markers that might've preserved from a previous test execution
        for (IMarker marker : ResourcesPlugin.getWorkspace().getRoot().findMarkers(GradleErrorMarker.ID, false, IResource.DEPTH_INFINITE)) {
            marker.delete();
        }
    }

    def "Display error marker if synchronization fails"() {
        setup:
        File projectDir = dir('error-marker-test') {
            file 'build.gradle', ''
        }
        importAndWait(projectDir)

        expect:
        numOfGradleErrorMarkers == 0

        when:
        File buildFile = new File(projectDir, 'build.gradle')
        buildFile.text = 'I_AM_ERROR'
        trySynchronizeAndWait(projectDir)

        then:
        numOfGradleErrorMarkers == 1
    }

    def "Error markers cleaned up after synchronization"() {
        setup:
        File projectDir = dir('error-marker-test') {
            file 'build.gradle', ''
        }
        importAndWait(projectDir)
        File buildFile = new File(projectDir, 'build.gradle')
        buildFile.text = 'I_AM_ERROR'

        when:
        trySynchronizeAndWait(projectDir)

        then:
        numOfGradleErrorMarkers == 1

        when:
        buildFile.text = ''
        trySynchronizeAndWait(projectDir)

        then:
        numOfGradleErrorMarkers == 0
    }

    def "Only related markers are updated"() {
        setup:
        File projectDir1 = dir('error-marker-test-1') {
            file 'build.gradle', ''
        }
        File projectDir2 = dir('error-marker-test-2') {
            file 'build.gradle', ''
        }
        importAndWait(projectDir1)
        importAndWait(projectDir2)
        File buildFile1 = new File(projectDir1, 'build.gradle')
        File buildFile2 = new File(projectDir2, 'build.gradle')
        buildFile1.text = 'I_AM_ERROR'
        buildFile2.text = 'I_AM_ERROR'

        when:
        trySynchronizeAndWait(projectDir1)

        then:
        numOfGradleErrorMarkers == 1

        when:
        trySynchronizeAndWait(projectDir2)

        then:
        numOfGradleErrorMarkers == 2

        when:
        buildFile1.text = ''
        trySynchronizeAndWait(projectDir1)

        then:
        numOfGradleErrorMarkers == 1
        findProject('error-marker-test-2').findMember(gradleErrorMarkers[0].resource.projectRelativePath)
    }

    def "Unlinking a project cleans up error markers too"() {
        setup:
        File projectDir = dir('error-marker-test') {
            file 'build.gradle', ''
            file 'settings.gradle', 'include "sub"'
            dir('sub')
        }
        importAndWait(projectDir)
        File buildFile = new File(projectDir, 'build.gradle')
        File settingsFile = new File(projectDir, 'settings.gradle')
        File subBuildFile = new File(projectDir, 'sub/build.gradle')
        subBuildFile.text = 'I_AM_ERROR'

        when:
        trySynchronizeAndWait(projectDir)

        then:
        numOfGradleErrorMarkers == 1

        when:
        settingsFile.text = ''
        trySynchronizeAndWait(projectDir)

        then:
        findProject('sub')
        numOfGradleErrorMarkers == 0
    }


    def "Converts problem reports to error markers"() {
        setup:
        WorkspaceConfiguration w = CorePlugin.configurationManager().loadWorkspaceConfiguration()
        configurationManager.saveWorkspaceConfiguration(new WorkspaceConfiguration(w.gradleDistribution, w.gradleUserHome, w.javaHome, w.offline, w.buildScansEnabled, w.autoSync, w.arguments, w.jvmArguments, w.showConsoleView, w.showExecutionsView, w.experimentalModuleSupportEnabled , true, ""))
        File projectDir = dir('error-marker-test') {
            file 'build.gradle', '''
                import org.gradle.api.internal.GradleInternal
                import org.gradle.api.problems.Problems
                import org.gradle.api.problems.Severity

                def gradleInternal = gradle as GradleInternal
                def problems = gradleInternal.services.get(Problems)

                problems.forNamespace("buildscript").reporting {
                    it.id("id", 'My problem')
                        .details("Problem details")
                        .severity(Severity.WARNING)
                        .solution("Please use 'standard-plugin-2' instead of this plugin")
                }
            '''
        }

        when:
        importAndWait(projectDir)

        then:
        problemsApiErrorMarkers.size() == 1
        problemsApiErrorMarkers[0].getAttribute(IMarker.MESSAGE) == 'Problem details'
        problemsApiErrorMarkers[0].getAttribute(GradleErrorMarker.ATTRIBUTE_FQID) == 'generic:id'

        cleanup:
        CorePlugin.configurationManager().saveWorkspaceConfiguration(w)
    }
}
