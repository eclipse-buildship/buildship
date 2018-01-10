package org.eclipse.buildship.core.workspace.internal

import org.gradle.tooling.BuildException

import org.eclipse.core.runtime.CoreException

import org.eclipse.buildship.core.CorePlugin
import org.eclipse.buildship.core.configuration.GradleProjectNature
import org.eclipse.buildship.core.notification.UserNotification
import org.eclipse.buildship.core.operation.ToolingApiStatus
import org.eclipse.buildship.core.operation.ToolingApiStatus.ToolingApiStatusType
import org.eclipse.buildship.core.test.fixtures.ProjectSynchronizationSpecification
import org.eclipse.buildship.core.workspace.internal.ImportRootProjectOperation.ImportRootProjectException

class ImportingBrokenProject extends ProjectSynchronizationSpecification {

    File projectDir

    def setup() {
        projectDir = dir('broken-project') {
            file 'build.gradle', ''
            file 'settings.gradle', 'include "sub"'
            dir('sub') {
                file 'build.gradle', 'I_AM_ERROR'
            }
        }
        registerService(UserNotification, Mock(UserNotification))
    }

    def "can import the root project of a broken build"() {
        when:
        boolean result = importAndWait(projectDir)

        then:
        thrown(BuildException)
        findProject('broken-project')
        !findProject('sub')
    }

    def "if the root project of a broken build is already part of the workspace then the Gradle nature is assigned to it"() {
        when:
        newProject('broken-project')
        importAndWait(projectDir)

        then:
        thrown(BuildException)
        CorePlugin.workspaceOperations().allProjects.size() == 1
        GradleProjectNature.isPresentOn(findProject('broken-project'))
    }

    def "can import the root project of a broken build, even if the root project name is already taken in the workspace"() {
        setup:
        dir('another') {
            File existingProjectLocation = dir('broken-project')
            importAndWait(existingProjectLocation)
        }

        when:
        importAndWait(projectDir)

        then:
        thrown(BuildException)
        findProject("broken-project_").location.toFile() == projectDir.canonicalFile
    }

    def "importing the root project of a broken build fails if the root dir is the workspace root"() {
        when:
        importAndWait(getWorkspaceDir())

        then:
        thrown(ImportRootProjectException)
        allProjects().empty
    }
}
