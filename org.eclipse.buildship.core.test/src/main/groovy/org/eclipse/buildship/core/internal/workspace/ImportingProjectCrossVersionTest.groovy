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
import org.junit.jupiter.api.Assertions;
import spock.lang.Ignore
import spock.lang.IgnoreIf
import spock.lang.Unroll

import org.eclipse.core.runtime.IStatus

import org.eclipse.buildship.core.GradleDistribution
import org.eclipse.buildship.core.internal.CompatibilityChecker
import org.eclipse.buildship.core.internal.operation.ToolingApiStatus
import org.eclipse.buildship.core.internal.test.fixtures.ProjectSynchronizationSpecification

class ImportingProjectCrossVersionTest extends ProjectSynchronizationSpecification {

    File multiProjectDir
    File compositeProjectDir
    File simpleProjectDir

    def setup() {
        multiProjectDir = dir('multi-project-build') {
            file 'settings.gradle', '''
                rootProject.name = 'root'
                include 'sub1'
                include 'sub2'
                include 'sub2:subSub1'
            '''

            file 'build.gradle', '''
                description = 'a sample root project'
                task myTask {}
            '''

            dir('sub1') {
                file 'build.gradle', '''
                    description = 'sub project 1'
                '''
            }
            dir('sub2') {
                file 'build.gradle', '''
                    description = 'sub project 2'
                '''

                dir('subSub1') {
                    file 'build.gradle', '''
                        description = 'subSub project 1 of sub project 2'
                    '''
                }
            }
        }

        compositeProjectDir = dir('composite-build') {
            file 'settings.gradle', '''
                rootProject.name='root'
                includeBuild 'included1'
                includeBuild 'included2'
            '''
            dir('included1') {
                dir('sub1')
                dir('sub2')
                file 'settings.gradle', '''
                    rootProject.name = 'included1'
                    include 'sub1', 'sub2'
                '''
            }
            dir('included2') {
                dir('sub1')
                dir('sub2')
                file 'settings.gradle', '''
                    rootProject.name = 'included2'
                    include 'sub1', 'sub2'
                '''
            }
        }

        simpleProjectDir = dir('simple-build') {
            file 'settings.gradle', '''
                rootProject.name = 'simple-build'
            '''
            file 'build.gradle', '''
                apply plugin: 'java'
                description = 'a simple project.'
                task myTask {}
            '''
            dir('src/main/java/pkg') {
                file 'Main.java', '''
                    package pkg;

                    public class Main {
                        public static void main(String[] args) {
                            System.out.println("Hello, world!");
                        }
                    }
                '''
            }
        }
    }


    @Unroll
    def "Can return correct import result and bypass compatibility check with Gradle #distribution.version"(GradleDistribution distribution) {
        when:
        System.setProperty(CompatibilityChecker.BYPASS_COMPATIBILITY_CHECK_KEY, "true")
        def importResult = tryImportAndWait(simpleProjectDir, distribution)

        then:
        if (importResult.status.isOK()) {
            Assertions.assertEquals(allProjects().size(), 1)
            Assertions.assertEquals(numOfGradleErrorMarkers, 0)
            Assertions.assertEquals(getPlatformLogErrors().size(), 0)
        } else {
            Assertions.assertTrue(importResult.status instanceof ToolingApiStatus)
            Assertions.assertNotEquals(importResult.status.getCode(), ToolingApiStatus.ToolingApiStatusType.INCOMPATIBILITY_JAVA.ordinal())
        }

        cleanup:
        System.setProperty(CompatibilityChecker.BYPASS_COMPATIBILITY_CHECK_KEY, "false")

        where:
        distribution << getSupportedGradleDistributions('>=3.0', true)
    }

    @Unroll
    def "Can return correct import result with Gradle #distribution.version"(GradleDistribution distribution) {
        when:
        def importResult = tryImportAndWait(simpleProjectDir, distribution)

        then:
        if (importResult.status.isOK()) {
            Assertions.assertEquals(1, allProjects().size())
            Assertions.assertEquals(0, numOfGradleErrorMarkers)
            Assertions.assertEquals(0, getPlatformLogErrors().size())
        } else {
            Assertions.assertTrue(importResult.status instanceof ToolingApiStatus, describeStatus(distribution, importResult.status))
            if (distributionUnavailable(importResult.status)) {
                println "Cannot verify the Java compatibility check for ${distribution.displayName}: the Gradle distribution could not be obtained (${importResult.status.message})"
            } else {
                Assertions.assertEquals(ToolingApiStatus.ToolingApiStatusType.INCOMPATIBILITY_JAVA.ordinal(), importResult.status.getCode(), describeStatus(distribution, importResult.status))
            }
        }

        where:
        distribution << getSupportedGradleDistributions('>=3.0', true)
    }

    def "An unobtainable Gradle distribution is reported as a connection failure"() {
        setup:
        GradleDistribution missing = GradleDistribution.forRemoteDistribution(new File(testDir, 'no-such-distribution.zip').toURI())

        when:
        def importResult = tryImportAndWait(simpleProjectDir, missing)

        then:
        importResult.status instanceof ToolingApiStatus
        importResult.status.getCode() == ToolingApiStatus.ToolingApiStatusType.CONNECTION_FAILED.ordinal()
        distributionUnavailable(importResult.status)
    }

    @Unroll
    def "Can import a multi-project build with Gradle #distribution.version"(GradleDistribution distribution) {
        when:
        importAndWait(multiProjectDir, distribution)

        then:
        allProjects().size() == 4
        numOfGradleErrorMarkers == 0
        findProject('root')
        findProject('sub1')
        findProject('sub2')
        findProject('subSub1')

        where:
        distribution << supportedGradleDistributions
    }

    @Unroll
    def "Included builds imported with de-duplicated names for #distribution.version"(GradleDistribution distribution) {
        when:
        importAndWait(compositeProjectDir, distribution)

        then:
        allProjects().size() == 7
        findProject('root')
        findProject('included1')
        findProject('included2')
        findProject('included1-sub1')
        findProject('included1-sub2')
        findProject('included2-sub1')
        findProject('included2-sub2')

        where:
        distribution << getSupportedGradleDistributions('>=5.5')
    }

	@Unroll
	def "Can handle duplicated build includes"(GradleDistribution distribution) {
		given:
		file('composite-build/included1/settings.gradle') << '''
			includeBuild '../included2'
        '''

		when:
		importAndWait(compositeProjectDir, distribution)

		then:
		allProjects().size() == 7
		findProject('root')
		findProject('included1')
		findProject('included2')
		findProject('included1-sub1')
		findProject('included1-sub2')
		findProject('included2-sub1')
		findProject('included2-sub2')

		where:
        distribution << getSupportedGradleDistributions('>=4.10')
	}

	@Unroll
	def "Can handle cyclic build includes"(GradleDistribution distribution) {
		given:
		file('composite-build/included1/settings.gradle') << '''
			includeBuild '../included2'
        '''
		file('composite-build/included2/settings.gradle') << '''
			includeBuild '../included1'
        '''

		when:
		importAndWait(compositeProjectDir, distribution)

		then:
		allProjects().size() == 7
		findProject('root')
		findProject('included1')
		findProject('included2')
		findProject('included1-sub1')
		findProject('included1-sub2')
		findProject('included2-sub1')
		findProject('included2-sub2')

		where:
		distribution << getSupportedGradleDistributions('>=6.8')
	}

    @Unroll
    def "Can handle cycle including the root build"(GradleDistribution distribution) {
        given:
        file('composite-build/settings.gradle').text = '''
            rootProject.name = 'root'
            includeBuild 'included1'
        '''
        file('composite-build/included1/settings.gradle').text = '''
            rootProject.name = 'included1'
            includeBuild '..'
        '''

        when:
        importAndWait(compositeProjectDir, distribution)

        then:
        allProjects().size() == 2
        findProject('root')
        findProject('included1')

        where:
        distribution << getSupportedGradleDistributions('>=6.8.1')
    }

    @Unroll
    def "Can handle self-include of root build"(GradleDistribution distribution) {
        // For context, see: https://github.com/gradle/gradle/pull/15817
        given:
        file('composite-build/settings.gradle').text = '''
            rootProject.name = 'root'
			includeBuild '.'
        '''

        when:
        importAndWait(compositeProjectDir, distribution)

        then:
        allProjects().size() == 1
        findProject('root')

        where:
        distribution << getSupportedGradleDistributions('>=6.8.1')
    }

    @Ignore("TODO Buildship doesn't de-duplicate project names which makes the synchronization fail")
    @Unroll
    def "Included builds imported but not de-duplicated names for #distribution.version"(GradleDistribution distribution) {
        when:
        importAndWait(compositeProjectDir, distribution)

        then:
        allProjects().size() == 7
        findProject('root')
        findProject('included1')
        findProject('included2')
        findProject('included1-sub1')
        findProject('included1-sub2')
        findProject('included2-sub1')
        findProject('included2-sub2')

        where:
        distribution << getSupportedGradleDistributions('<4.0 >=3.3')
    }

    @Unroll
    @IgnoreIf({ JavaVersion.current().isJava9Compatible() })
    def "Included builds ignored for #distribution.version"(GradleDistribution distribution) {
        when:
        importAndWait(compositeProjectDir, distribution)

        then:
        allProjects().size() == 1
        findProject('root')

        where:
        distribution << getSupportedGradleDistributions('<3.3 >=3.1')
    }

    /**
     * Whether the status was caused by a Gradle distribution that could not be downloaded or
     * unpacked. The Tooling API reports that as an ordinary connection failure, so the status code
     * alone cannot tell it apart from a genuinely misclassified error.
     */
    private static boolean distributionUnavailable(IStatus status) {
        if (status.getCode() != ToolingApiStatus.ToolingApiStatusType.CONNECTION_FAILED.ordinal()) {
            return false
        }
        Set<Throwable> seen = new HashSet<>()
        Throwable current = status.exception
        while (current != null && seen.add(current)) {
            String message = current.message
            if (message != null && (message.contains('Could not install Gradle distribution from')
                    || message.contains('The specified Gradle distribution'))) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private static String describeStatus(GradleDistribution distribution, IStatus status) {
        StringWriter stacktrace = new StringWriter()
        status.exception?.printStackTrace(new PrintWriter(stacktrace))
        "import with ${distribution.displayName} returned code ${status.getCode()}: ${status.message}\n${stacktrace}"
    }
}
