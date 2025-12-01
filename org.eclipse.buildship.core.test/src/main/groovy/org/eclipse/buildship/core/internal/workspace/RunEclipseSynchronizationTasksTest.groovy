/*******************************************************************************
 * Copyright (c) 2023 Gradle Inc. and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.core.internal.workspace

import org.eclipse.buildship.core.GradleDistribution
import org.eclipse.buildship.core.internal.CorePlugin
import org.eclipse.buildship.core.internal.console.ProcessStreamsProvider
import org.eclipse.buildship.core.internal.test.fixtures.ProjectSynchronizationSpecification
import org.eclipse.buildship.core.internal.test.fixtures.TestProcessStreamProvider

class RunEclipseSynchronizationTasksTest extends ProjectSynchronizationSpecification {


    String getSyncConsoleOutput() {
        TestProcessStreamProvider testStreams = CorePlugin.processStreamsProvider()
        testStreams.testStream.out
    }

    def "executes tasks from eclipse plugin configuration"() {
        setup:
        def location = dir('run-eclipse-sync-task-1') {
            file 'build.gradle', '''
                plugins {
                    id 'eclipse'
                }

                task foo {
                    doLast {
                        println 'foo'
                    }
                }

                eclipse {
                    synchronizationTasks foo
                }
             '''
         }

         when:
         importAndWait(location)

         then:
         findProject('run-eclipse-sync-task-1')
         waitFor { syncConsoleOutput.contains "Task :foo" }
    }
}
