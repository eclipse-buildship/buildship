/*******************************************************************************
 * Copyright (c) 2019 Gradle Inc.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.core.internal.workspace

import org.eclipse.buildship.core.GradleDistribution
import org.eclipse.buildship.core.SynchronizationResult
import org.eclipse.buildship.core.internal.UnsupportedConfigurationException
import org.eclipse.buildship.core.internal.test.fixtures.ProjectSynchronizationSpecification

class SynchronizingRenamedProject extends ProjectSynchronizationSpecification {

    def "Project name is updated when it changes in Gradle"() {
        setup:
        def sample = dir('sample') {
            file 'settings.gradle', ''
        }
        importAndWait(sample)

        expect:
        findProject(sample.name)

        when:
        renameInGradle(sample, "custom-sample")
        synchronizeAndWait(sample)

        then:
        findProject('custom-sample')
    }

    def "Project name is not updated if it conflicts with unrelated workspace project prior to 5.5"() {
        setup:
        def sample = dir('sample')
        importAndWait(sample, GradleDistribution.forVersion("5.4.1"))
        def alreadyThere = newProject("already-there")

        expect:
        findProject(sample.name)

        when:
        renameInGradle(sample, "already-there")
        SynchronizationResult result = trySynchronizeAndWait(sample)

        then:
        result.status.exception instanceof UnsupportedConfigurationException
        findProject('already-there') == alreadyThere
    }

    def "Project name is not updated if it conflicts with unrelated workspace project"() {
        setup:
        def sample = dir('sample') {
            file 'settings.gradle', ''
        }
        importAndWait(sample)
        def alreadyThere = newProject("already-there")

        expect:
        findProject(sample.name)

        when:
        renameInGradle(sample, "already-there")
        SynchronizationResult result = trySynchronizeAndWait(sample)

        then:
        result.status.exception instanceof UnsupportedConfigurationException
        findProject('already-there') == alreadyThere
    }

    def "Projects in the same build can be renamed in cycles"() {
        def root = dir('root') {
            dir 'a'
            dir 'b'
            file 'settings.gradle', "include 'a', 'b'"
        }
        importAndWait(root)
        fileTree(root) {
            renameInGradle(dir('a'), "b")
            renameInGradle(dir('b'), "a")
        }

        when:
        synchronizeAndWait(root)

        then:
        findProject("a").location.lastSegment() == "b"
        findProject("b").location.lastSegment() == "a"
    }

    def "Cyclic renaming also works for new subprojects"() {
        def root = dir('root') {
            dir 'a'
            file 'settings.gradle', "include 'a'"
        }
        importAndWait(root)
        fileTree(root) {
            dir 'b'
            file('settings.gradle').text = "include 'a', 'b'"
            renameInGradle(dir('a'), "b")
            renameInGradle(dir('b'), "a")
        }

        when:
        synchronizeAndWait(root)

        then:
        findProject("a").location.lastSegment() == "b"
        findProject("b").location.lastSegment() == "a"
    }

    private void renameInGradle(File project, String newName) {
        fileTree(project) {
            file 'build.gradle', """
                apply plugin: 'eclipse'
                eclipse.project.name = '${newName}'
            """
        }
    }

}
