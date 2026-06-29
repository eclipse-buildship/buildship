/*******************************************************************************
 * Copyright (c) 2023 Gradle Inc. and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.ui.internal.launch

import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.IType

import org.eclipse.buildship.core.GradleDistribution
import org.eclipse.buildship.core.internal.util.gradle.GradleVersion
import org.eclipse.buildship.ui.internal.test.fixtures.ProjectSynchronizationSpecification

@spock.lang.Ignore
abstract class TestLaunchShortcutValidatorTest extends ProjectSynchronizationSpecification {

    def "Launch shortcut enabled on test sources"() {
        setup:
        importAndWait(createProjectWithSources(dependencyConfiguration), GradleDistribution.forVersion(gradleVersion), [], jdk == "8" ? new File(System.getProperty("jdk8.location")) : null)

        when:
        IJavaProject project = findJavaProject('project-with-sources')
        IType type = project.findType('LibrarySpec')

        then:
        type != null
        testLaunchShortcutEnabledOn(type)

        where:
        gradleVersion                   | dependencyConfiguration | jdk
        '4.3'                           | 'compile'               | "8"
        GradleVersion.current().version | 'implementation'        | null
    }

    def "Launch debug shortcut enabled on test sources"() {
        setup:
        importAndWait(createProjectWithSources('implementation'), GradleDistribution.fromBuild())

        when:
        IJavaProject project = findJavaProject('project-with-sources')
        IType type = project.findType('LibrarySpec')

        then:
        testDebugLaunchShortcutEnabledOn(type)
    }

    def "Launch debug shortcut disabled for projects using Gradle < 5.6"() {
        setup:
        importAndWait(createProjectWithSources(), GradleDistribution.forVersion('5.5.1'), [], new File(System.getProperty("jdk11.location")))

        when:
        IJavaProject project = findJavaProject('project-with-sources')
        IType type = project.findType('LibrarySpec')

        then:
        !testDebugLaunchShortcutEnabledOn(type)
    }

    def "Launch shortcut disabled on production sources"() {
        setup:
        importAndWait(createProjectWithSources(dependencyConfiguration), GradleDistribution.forVersion(gradleVersion), [], jdk == "8" ? new File(System.getProperty("jdk8.location")) : null)

        when:
        IJavaProject project = findJavaProject('project-with-sources')
        IType type = project.findType('Library')

        then:
        type != null
        !testLaunchShortcutEnabledOn(type)

        where:
        gradleVersion                   | dependencyConfiguration | jdk
        '4.3'                           | 'compile'               | "8"
        GradleVersion.current().version | 'implementation'        | null
    }

    def "Launch shortcut disabled on non-source type"() {
        setup:
        importAndWait(createProjectWithSources('implementation'))

        when:
        IJavaProject project = findJavaProject('project-with-sources')
        IType type = project.findType('com.google.common.base.Predicate')

        then:
        type != null
        !testLaunchShortcutEnabledOn(type)
    }

    private File createProjectWithSources(configuration = 'compile') {
        dir('project-with-sources') {
            file 'build.gradle', """
                apply plugin: 'java'

                ${jcenterRepositoryBlock}

                dependencies {
                    $configuration 'com.google.guava:guava:18.0'
                }
            """
            dir('src/main/java') {
                file  'Library.java', 'public class Library { }'
            }
            dir('src/test/java') {
                file  'LibrarySpec.java', 'public class LibrarySpec { }'
            }
        }
    }

    private boolean testLaunchShortcutEnabledOn(Object... receiver) {
        isSelectionPropertyEnabled(SelectionPropertyTester.PROPERTY_NAME_SELECTION_CAN_EXECUTE_TEST_RUN, receiver)
    }

    private boolean testDebugLaunchShortcutEnabledOn(Object... receiver) {
        isSelectionPropertyEnabled(SelectionPropertyTester.PROPERTY_NAME_SELECTION_CAN_EXECUTE_TEST_DEBUG, receiver)
    }

    private boolean isSelectionPropertyEnabled(String property, Object... receiver) {
        new SelectionPropertyTester().test(Arrays.asList(receiver), property, new Object[0], null)
    }
}
