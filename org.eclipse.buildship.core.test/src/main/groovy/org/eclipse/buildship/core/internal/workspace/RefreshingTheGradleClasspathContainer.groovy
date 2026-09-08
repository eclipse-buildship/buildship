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

import org.eclipse.jdt.core.IClasspathContainer
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.JavaCore

import org.eclipse.buildship.core.internal.test.fixtures.ProjectSynchronizationSpecification

class RefreshingTheGradleClasspathContainer extends ProjectSynchronizationSpecification {

    def "Update the project classpath"() {
        setup:
        File location = importNewSimpleProject('simpleproject')
        IJavaProject project = findJavaProject('simpleproject')
        defineLocalGroovyDependency(new File(location, 'build.gradle'))

        when:
        synchronizeAndWait(project.project)

        then:
        hasLocalGroovyDependencyDefinedInClasspathContainer(project)
    }

    def "Update with unresolved dependencies creates error markers"(){
        setup:
        File location = importNewSimpleProject('simpleproject')
        IJavaProject project = findJavaProject('simpleproject')
        defineLocalGroovyAndSomeUnresolvedDependencies(new File(location, 'build.gradle'))

        when:
        synchronizeAndWait(project.project)

        then:
        gradleErrorMarkers.size() == 2
    }

    def "Update changes the classpath of all related projects"() {
        setup:
        File location = importNewMultiProject('rootproject', 'subproject')
        IJavaProject rootProject = findJavaProject('rootproject')
        IJavaProject subProject = findJavaProject('subproject')

        defineLocalGroovyDependency(new File(location, 'build.gradle'))
        defineLocalGroovyDependency(new File("$location/subproject", 'build.gradle'))

        when:
        synchronizeAndWait(subProject.project)

        then:
        hasLocalGroovyDependencyDefinedInClasspathContainer(rootProject)
        hasLocalGroovyDependencyDefinedInClasspathContainer(subProject)
    }

    def "Update doesn't change the classpath of unrelated projects"() {
        setup:
        File unrelatedProjectLocation = importNewSimpleProject('unrelatedproject')
        File location = importNewSimpleProject('simpleproject')
        IJavaProject unrelatedProject = findJavaProject('unrelatedproject')
        IJavaProject project = findJavaProject('simpleproject')

        defineLocalGroovyDependency(new File(location, 'build.gradle'))
        defineLocalGroovyDependency(new File(unrelatedProjectLocation, 'build.gradle'))

        when:
        synchronizeAndWait(project.project)

        then:
        hasLocalGroovyDependencyDefinedInClasspathContainer(project)
        !hasLocalGroovyDependencyDefinedInClasspathContainer(unrelatedProject)
    }

    def "Updates multiple project roots at the same time"() {
        setup:
        File firstLocation = importNewSimpleProject('first')
        File secondLocation = importNewSimpleProject('second')
        IJavaProject firstProject = findJavaProject('first')
        IJavaProject secondProject = findJavaProject('second')

        defineLocalGroovyDependency(new File(firstLocation, 'build.gradle'))
        defineLocalGroovyDependency(new File(secondLocation, 'build.gradle'))

        when:
        synchronizeAndWait(firstProject.project)
        synchronizeAndWait(secondProject.project)

        then:
        hasLocalGroovyDependencyDefinedInClasspathContainer(firstProject)
        hasLocalGroovyDependencyDefinedInClasspathContainer(secondProject)
    }

    private def importNewSimpleProject(String projectName) {
        def location = newSimpleGradleProject(projectName)
        importAndWait(location)
        location
    }

    private def importNewMultiProject(String rootName, String subName) {
        def location = newGradleMultiProject(rootName, subName)
        importAndWait(location)
        location
    }

    private def newSimpleGradleProject(String projectName) {
        dir(projectName) {
            file 'build.gradle', 'apply plugin: "java"'
            dir 'src/main/java'
        }
    }

    private def newGradleMultiProject(String rootProjectName, String subProjectName) {
        dir(rootProjectName) {
            file 'build.gradle', 'apply plugin: "java"'
            file 'settings.gradle', "include '$subProjectName'"
            dir 'src/main/java'
            dir(subProjectName) {
                file 'build.gradle', 'apply plugin: "java"'
                dir 'src/main/java'
            }
        }
    }

    private static def defineLocalGroovyDependency(File buildScript) {
        buildScript << '''
            dependencies {
                implementation localGroovy()
            }
        '''
    }

    private static def defineLocalGroovyAndSomeUnresolvedDependencies(File buildScript) {
        buildScript << '''
            dependencies {
                implementation localGroovy()
                implementation 'this:isalso:unresolved'
                implementation 'this:again:unresolved'
            }
        '''
    }

    private static def hasLocalGroovyDependencyDefinedInClasspathContainer(IJavaProject javaProject) {
        IClasspathContainer rootContainer = JavaCore.getClasspathContainer(GradleClasspathContainer.CONTAINER_PATH, javaProject)
        rootContainer.classpathEntries.find  { it.path.toPortableString().contains('groovy-3') } != null
    }

}
