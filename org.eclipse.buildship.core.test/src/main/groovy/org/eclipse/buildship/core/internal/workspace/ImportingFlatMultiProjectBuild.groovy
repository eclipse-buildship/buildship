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

import org.eclipse.buildship.core.internal.CorePlugin
import org.eclipse.buildship.core.internal.Logger
import org.eclipse.buildship.core.internal.configuration.GradleProjectNature
import org.eclipse.buildship.core.internal.test.fixtures.EclipseProjects
import org.eclipse.buildship.core.internal.test.fixtures.ProjectSynchronizationSpecification

import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IProjectDescription
import org.eclipse.core.resources.IResource
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jdt.core.JavaCore

class ImportingFlatMultiProjectBuild extends ProjectSynchronizationSpecification {

    File sampleDir
    File moduleADir
    File moduleBDir

    def setup() {
        createSampleProject()
        importAndWait(sampleDir)
    }

    def "If a new project is added to the Gradle build, it is imported into the workspace"() {
        setup:
        fileTree(sampleDir) {
            file('settings.gradle') << """
               includeFlat 'moduleC'
            """
            dir('../moduleC') {
                file 'build.gradle', "apply plugin: 'java'"
                dir 'src/main/java'
            }
        }

        when:
        synchronizeAndWait(findProject('sample'))

        then:
        IProject project = findProject('moduleC')
        project != null
        GradleProjectNature.isPresentOn(project)
    }

    def "An existing workspace project is transformed to a Gradle project when included in a Gradle build"() {
        setup:
        fileTree(sampleDir).file('settings.gradle') << """
           includeFlat 'moduleC'
        """
        def project = EclipseProjects.newProject("moduleC", new File(sampleDir.parent, "moduleC"))

        when:
        synchronizeAndWait(findProject('sample'))

        then:
        GradleProjectNature.isPresentOn(project)
    }

    def "Nonexisting sub projects are ignored"() {
        setup:
        fileTree(sampleDir).file('settings.gradle') << """
           includeFlat 'moduleC'
        """
        def logger = Mock(Logger)
        environment.registerService(Logger, logger)

        when:
        synchronizeAndWait(findProject('sample'))

        then:
        0 * logger.error(_)
    }

    private File createSampleProject() {
         dir('root') {
            sampleDir = sample {
                file 'build.gradle', """
                    allprojects {
                        ${jcenterRepositoryBlock}
                        apply plugin: 'java'
                    }
                """
                file 'settings.gradle', """
                    includeFlat 'moduleA'
                    includeFlat 'moduleB'
                """
            }

            moduleADir = moduleA {
                file 'build.gradle', "apply plugin: 'java'"
                dir 'src/main/java'
            }
            moduleBDir = moduleB {
                file 'build.gradle', "apply plugin: 'java'"
                dir 'src/main/java'
            }
        }
    }

}
