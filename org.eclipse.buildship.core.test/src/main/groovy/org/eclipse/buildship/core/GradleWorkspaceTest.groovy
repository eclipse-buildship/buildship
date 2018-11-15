package org.eclipse.buildship.core

import org.eclipse.core.resources.IProject
import org.eclipse.core.runtime.NullProgressMonitor

import org.eclipse.buildship.core.internal.CorePlugin
import org.eclipse.buildship.core.internal.configuration.GradleProjectNature
import org.eclipse.buildship.core.internal.test.fixtures.ProjectSynchronizationSpecification

class GradleWorkspaceTest extends ProjectSynchronizationSpecification {

    def "Cannot get a null build"() {
        expect:
        !GradleCore.workspace.getBuild(null).present
    }

    def "Cannot get a Gradle build if target project is inaccessible"() {
        expect:
        !GradleCore.workspace.getBuild(newClosedProject("GradleWorkspaceTest")).present
    }

    def "Cannot get a Gradle build if target project is not a Gradle project"() {
        expect:
        !GradleCore.workspace.getBuild(newProject("GradleWorkspaceTest")).present
    }

    def "Can get a Gradle build via valid Gradle project"() {
        setup:
        importAndWait(dir("GradleWorkspaceTest"))
        IProject project = findProject("GradleWorkspaceTest")

        expect:
        GradleCore.workspace.getBuild(project).present
    }

    def "Cannot create a null build"() {
        when:
        GradleCore.workspace.createBuild(null)

        then:
        thrown(NullPointerException)
    }

    def "Can create a Gradle build"() {
        setup:
        BuildConfiguration configuration = BuildConfiguration.forRootProjectDirectory(dir('GradleWorkspaceTest')).build()

        expect:
        GradleCore.workspace.createBuild(configuration)
    }
}
