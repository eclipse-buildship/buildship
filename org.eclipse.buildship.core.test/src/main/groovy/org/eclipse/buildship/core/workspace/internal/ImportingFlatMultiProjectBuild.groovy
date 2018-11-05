package org.eclipse.buildship.core.workspace.internal

import org.eclipse.buildship.core.CorePlugin
import org.eclipse.buildship.core.Logger
import org.eclipse.buildship.core.configuration.GradleProjectNature
import org.eclipse.buildship.core.test.fixtures.EclipseProjects
import org.eclipse.buildship.core.test.fixtures.ProjectSynchronizationSpecification

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
