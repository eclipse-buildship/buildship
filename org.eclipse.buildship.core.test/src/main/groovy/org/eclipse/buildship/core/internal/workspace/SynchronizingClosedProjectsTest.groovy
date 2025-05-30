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

import org.eclipse.core.resources.IMarker
import org.eclipse.jdt.core.IClasspathEntry
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.launching.IRuntimeClasspathEntry
import org.eclipse.jdt.launching.JavaRuntime

import org.eclipse.buildship.core.internal.CorePlugin
import org.eclipse.buildship.core.internal.console.ProcessStreamsProvider
import org.eclipse.buildship.core.internal.test.fixtures.ProjectSynchronizationSpecification
import org.eclipse.buildship.core.internal.test.fixtures.TestProcessStreamProvider

class SynchronizingClosedProjectsTest extends ProjectSynchronizationSpecification {

    File buildA
    File buildB
    File buildC

    def setup() {
        buildA = dir("buildA") {
            file "build.gradle", """
                group = 'org.test'
                version = '1.0'
                apply plugin: 'java-library'
                dependencies {
                    testImplementation "org.test:b1:1.0"
                }
            """
            file "settings.gradle",  """
                includeBuild 'buildB'
                includeBuild 'buildC'
            """
            dir("src/main/java") {
                file 'Dummy.java', "public class Dummy {}"
            }

            buildB = dir("buildB") {
                file "build.gradle", """
                allprojects {
                    group = 'org.test'
                    version = '1.0'
                    apply plugin: 'java-library'
                }
                project(':b1') {
                    apply plugin: 'eclipse'
                    dependencies {
                        testImplementation "org.test:buildC:1.0"
                        implementation project(":b2")
                        testImplementation project(path: ":b2", configuration: "testArtifacts")
                    }
                    eclipse {
                        classpath.file.whenMerged { cp ->
                            def dep = entries.find { it.path.endsWith('b2') }
                            def sourceJar = this.project(":b2").tasks.getByName("sourceJar")
                            dep.buildDependencies(sourceJar)
                            dep.publicationSourcePath = cp.fileReference(sourceJar.archiveFile.get().asFile)
                        }
                    }
                }
                project(':b2') {
                    configurations {
                        testArtifacts
                    }
                    task testJar(type: Jar) {
                        archiveClassifier = 'tests'
                        from sourceSets.test.output.classesDirs
                    }
                    artifacts {
                        testArtifacts testJar
                    }
                    task sourceJar(type: Jar, dependsOn: classes) {
                        archiveClassifier = 'sources'
                        from sourceSets.main.allSource
                    }
                }
            """
                file "settings.gradle",  """
                include ':b1', ':b2'
            """
                dir("b1") {
                    dir("src/main/java") {
                        file 'Dummy.java', "public class Dummy {}"
                    }
                }
                dir("b2") {
                    dir("src/main/java") {
                        file 'Dummy.java', "public class Dummy {}"
                    }
                    dir("src/test/java") {
                        file 'Dummy.java', "public class Dummy {}"
                    }
                }
            }

            buildC = dir("buildC") {
                file "build.gradle", """
                group = 'org.test'
                version = '1.0'
                apply plugin: 'java-library'
            """
                file "settings.gradle",  """
            """
                dir("src/main/java") {
                    file 'Dummy.java', "public class Dummy {}"
                }
            }
        }
    }

    def "Closed Projects are substituted for binary dependencies in composites"() {
        when:
        importAndWait(buildA)

        then:
        allProjects().size() == 5

        when:
        findProject("buildC").close()
        synchronizeAndWait(buildA)
        IJavaProject javaProject = JavaCore.create(findProject('b1'))
        IRuntimeClasspathEntry[] classpath = projectRuntimeClasspath(javaProject)

        then:
        numOfGradleErrorMarkers == 0
        findProject('buildA')
        findProject('buildB')
        findProject('buildC')
        findProject('b2')

        !classpath.find { it.type ==IRuntimeClasspathEntry.PROJECT && it.path.lastSegment() == 'buildC' }
        classpath.find { it.type ==IRuntimeClasspathEntry.ARCHIVE && it.path.lastSegment() == 'buildC-1.0.jar' }
        waitFor { syncConsoleOutput.contains("Task :eclipseClosedDependencies") }
        syncConsoleOutput.contains( "Task :buildC:jar" )
    }

    def "Substitutions include dependencies from other configurations"() {
        when:
        tryImportAndWait(buildB)

        then:
        allProjects().size() == 3

        when:
        findProject("b2").close()
        trySynchronizeAndWait(buildB)
        IJavaProject javaProject = JavaCore.create(findProject('b1'))
        IRuntimeClasspathEntry[] classpath = projectRuntimeClasspath(javaProject)

        then:
        numOfGradleErrorMarkers == 1
        gradleErrorMarkers[0].getAttribute(IMarker.MESSAGE) == 'Unresolved dependency: org.test:buildC:1.0'
        !classpath.find { it.type ==IRuntimeClasspathEntry.PROJECT && it.path.lastSegment() == 'b2' }
        classpath.find { it.type ==IRuntimeClasspathEntry.ARCHIVE && it.path.lastSegment() == 'b2-1.0.jar' }
        classpath.find { it.type ==IRuntimeClasspathEntry.ARCHIVE && it.path.lastSegment() == 'b2-1.0-tests.jar' }
        // TODO: verify that the "b2-1.0-tests.jar" artifact is marked for the test classpath once https://github.com/gradle/gradle/pull/9484 is merged
        waitFor { syncConsoleOutput.contains ("Task :eclipseClosedDependencies") }
        syncConsoleOutput.contains("Task :b2:jar")
        syncConsoleOutput.contains("Task :b2:testJar")
    }

    def "Substitutes can have source attachments"() {
        when:
        tryImportAndWait(buildB)

        then:
        allProjects().size() == 3

        when:
        findProject("b2").close()
        trySynchronizeAndWait(buildB)
        IJavaProject javaProject = findJavaProject('b1')

        then:
        def dependency = javaProject.getResolvedClasspath(true).find { it.entryKind == IClasspathEntry.CPE_LIBRARY && it.path.toPortableString().contains('b2-1.0.jar') }
        dependency.sourceAttachmentPath != null

        waitFor { syncConsoleOutput.contains ("Task :eclipseClosedDependencies") }
        syncConsoleOutput.contains("Task :b2:sourceJar")
    }


    private String getSyncConsoleOutput() {
        TestProcessStreamProvider testStreams = CorePlugin.processStreamsProvider()
        testStreams.testStream.out
    }

    private IRuntimeClasspathEntry[] projectRuntimeClasspath(IJavaProject project) {
        IRuntimeClasspathEntry projectEntry = JavaRuntime.computeUnresolvedRuntimeClasspath(project).find { it.path == project.project.fullPath }
        JavaRuntime.resolveRuntimeClasspathEntry(projectEntry, project)
    }
}

