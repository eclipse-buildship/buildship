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

import org.gradle.api.JavaVersion
import spock.lang.Ignore
import spock.lang.IgnoreIf
import spock.lang.Issue

import org.eclipse.core.runtime.Path
import org.eclipse.jdt.core.IClasspathEntry
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.launching.IRuntimeClasspathEntry
import org.eclipse.jdt.launching.JavaRuntime

import org.eclipse.buildship.core.internal.test.fixtures.ProjectSynchronizationSpecification
import org.eclipse.buildship.core.internal.util.eclipse.PlatformUtils
import org.eclipse.buildship.core.GradleDistribution

class RuntimeClasspathTest extends ProjectSynchronizationSpecification {

    File location
    File buildFile

    def setup() {
        location = dir('sample-project') {
            file('settings.gradle') << "include 'a', 'b', 'c'"
            dir('a/src/main/java')
            dir('b/src/main/java')
            dir('c/src/main/java')
            buildFile = file 'build.gradle', """
                subprojects {
                    apply plugin: 'java'
                    ${jcenterRepositoryBlock}
                }
            """
        }
    }

    def "Project classpath has entries only from local Gradle classpath container"() {
        setup:
        buildFile << '''
            project(':a') {
                dependencies {
                    implementation 'log4j:log4j:1.2.17'
                }
            }

            project(':b') {
                dependencies {
                    implementation 'log4j:log4j:1.2.16'
                }
            }

            project(':c') {
                dependencies {
                    implementation project(':a')
                    implementation project(':b')
                    testImplementation 'junit:junit:4.12'
                }
            }
        '''
        importAndWait(location)

        when:
        IJavaProject javaProject = JavaCore.create(findProject('c'))
        IRuntimeClasspathEntry[] classpath = projectRuntimeClasspath(javaProject)

        then:
        classpath.find { it.path.lastSegment() == 'log4j-1.2.17.jar' }
        !classpath.find { it.path.lastSegment() == 'log4j-1.2.16.jar' }
    }

    def "Dependencies of project dependencies are removed from the runtime classpath"() {
        setup:
        buildFile << '''
            project(':a') {
                dependencies {
                    implementation 'org.springframework:spring-core:4.3.1.RELEASE'
                }
            }

            project(':b') {
                configurations.all {
                    exclude group: 'commons-logging'
                }

                dependencies {
                    implementation project(':a')
                }
            }
        '''
        importAndWait(location)

        when:
        IJavaProject javaProject = JavaCore.create(findProject('b'))
        IRuntimeClasspathEntry[] classpath = projectRuntimeClasspath(javaProject)

        then:
        !classpath.find { it.path.toPortableString().contains('commons-logging') }
    }

    @Ignore("Connectiong to this project with Gradle 4.3 seems to be broken")
    def "Dependencies are still on the runtime classpath"() {
        setup:
        new File(location, 'b/lib').mkdirs()
        buildFile << '''
            project(':b') {
                dependencies {
                    compile project(':a')
                    compile 'log4j:log4j:1.2.17'
                    compile files('lib')
                }
            }
        '''
        // TODO (donat) add test coverage for more recent versions
        importAndWait(location, GradleDistribution.forVersion('4.3'))

        when:
        IJavaProject javaProject = JavaCore.create(findProject('b'))
        IRuntimeClasspathEntry[] classpath = projectRuntimeClasspath(javaProject)

        then:
        classpath.find { it.type == IRuntimeClasspathEntry.PROJECT && it.path.lastSegment() == 'a' }
        classpath.find { it.type == IRuntimeClasspathEntry.PROJECT && it.path.lastSegment() == 'b' }
        classpath.find { it.type == IRuntimeClasspathEntry.ARCHIVE && it.path.lastSegment() == 'log4j-1.2.17.jar' }
        classpath.find { it.type == IRuntimeClasspathEntry.ARCHIVE && it.path.lastSegment() == 'lib' }
    }

    @Ignore("Connectiong to this project with Gradle 4.3 seems to be broken")
    @Issue("https://bugs.eclipse.org/bugs/show_bug.cgi?id=507206")
    def "Runtime classpath contains custom output folders"() {
        setup:
        // Another non-custom source directory is required for the default-directory to be set
        new File(location, 'a/src/test/java').mkdirs()
        buildFile << '''
            project(':a') {
                apply plugin: 'eclipse'

                eclipse {
                    classpath {
                        defaultOutputDir = file('default-output-dir')

                        file {
                            whenMerged {
                                def src = entries.find { it.path == 'src/main/java' }
                                src.output = 'custom-output-dir'
                            }
                        }
                    }
                }
            }

            project(':b') {
                dependencies {
                    implementation project(':a')
                }
            }
        '''
        // TODO (donat) add test coverage for more recent versions
        importAndWait(location, GradleDistribution.forVersion("4.3"))

        when:
        IJavaProject javaProject = JavaCore.create(findProject('b'))
        IRuntimeClasspathEntry[] classpath = projectRuntimeClasspath(javaProject)

        then:
        classpath.find { it.type == IRuntimeClasspathEntry.ARCHIVE && it.path.lastSegment() == 'custom-output-dir' }
        classpath.find { it.type == IRuntimeClasspathEntry.ARCHIVE && it.path.lastSegment() == 'default-output-dir' }
    }

    def "Gradle classpath containers from different projects always considered unequal"() { //
        setup:
        File external = dir('external-gradle-project') {
            file('build.gradle') << """
                apply plugin: 'java'
                ${jcenterRepositoryBlock}
                dependencies.implementation 'com.google.guava:guava:18.0'
            """
        }
        importAndWait(external)
        importAndWait(location)

        IJavaProject project = findJavaProject('a')
        project.setRawClasspath(project.rawClasspath + [JavaCore.newProjectEntry(new Path('/external-gradle-project'))] as IClasspathEntry[], null)
        IRuntimeClasspathEntry[] unresolvedClasspath = JavaRuntime.computeUnresolvedRuntimeClasspath(project)
        IRuntimeClasspathEntry[] resolvedClasspath = JavaRuntime.resolveRuntimeClasspath(unresolvedClasspath, createGradleLaunchConfig())

        expect:
        resolvedClasspath.find { it.path.lastSegment().contains 'guava' }
    }

    @Issue("https://github.com/eclipse/buildship/issues/1004")
    @IgnoreIf({ !PlatformUtils.supportsTestAttributes() })
    def "Project dependency test code is not on the classpath if without_test_code attribute is set"() {
        setup:
        // Another non-custom source directory is required for the default-directory to be set
        new File(location, 'a/src/main/java').mkdirs()
        new File(location, 'a/src/test/java').mkdirs()
        buildFile << '''
            project(':a') {
                apply plugin: 'java-library'
            }

            project(':b') {
                apply plugin: 'eclipse'

                dependencies {
                    implementation project(':a')
                }

                eclipse.classpath.file.whenMerged {
                    entries.findAll { it instanceof org.gradle.plugins.ide.eclipse.model.ProjectDependency }
                        .each { it.entryAttributes['without_test_code'] = 'true' }
                }
            }
        '''
        importAndWait(location)

        when:
        IJavaProject javaProject = JavaCore.create(findProject('b'))
        IRuntimeClasspathEntry[] classpath = projectRuntimeClasspath(javaProject)

        then:
        classpath.find { it.type == IRuntimeClasspathEntry.ARCHIVE && it.path.toPortableString() == '/a/bin/main' }
        !classpath.find { it.type == IRuntimeClasspathEntry.ARCHIVE && it.path.toPortableString() == '/a/bin/test' }
    }

    @Issue("https://github.com/eclipse-buildship/buildship/issues/1383")
    @IgnoreIf({ !PlatformUtils.supportsTestAttributes() })
    def "Test-only project dependency is absent from the runtime classpath when test code is excluded"() {
        setup:
        new File(location, 'a/src/main/java').mkdirs()
        new File(location, 'b/src/test/java').mkdirs()
        buildFile << '''
            project(':b') {
                dependencies {
                    testImplementation project(':a')
                }
            }
        '''
        importAndWait(location)

        when:
        IJavaProject javaProject = JavaCore.create(findProject('b'))
        IRuntimeClasspathEntry[] classpath = projectRuntimeClasspath(javaProject, true)

        then:
        !classpath.find { it.path.toPortableString() == '/a' }
        !classpath.find { it.path.toPortableString().startsWith('/a/bin') }
    }

    private IRuntimeClasspathEntry[] projectRuntimeClasspath(IJavaProject project, boolean excludeTestCode = false) {
        IRuntimeClasspathEntry projectEntry = JavaRuntime.computeUnresolvedRuntimeClasspath(project).find { it.path == project.project.fullPath }
        JavaRuntime.resolveRuntimeClasspathEntry(projectEntry, project, excludeTestCode)
    }
}
