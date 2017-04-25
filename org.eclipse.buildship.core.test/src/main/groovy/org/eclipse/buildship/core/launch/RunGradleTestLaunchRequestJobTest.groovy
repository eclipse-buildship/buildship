package org.eclipse.buildship.core.launch


import org.eclipse.jdt.core.IType

import org.eclipse.buildship.core.CorePlugin
import org.eclipse.buildship.core.configuration.RunConfiguration

class RunGradleTestLaunchRequestJobTest extends BaseLaunchRequestJobTest {

    File projectDir

    void setup() {
        projectDir = dir('java-launch-config') {
            file 'build.gradle', """
                apply plugin: 'java'
                repositories.jcenter()
                dependencies.testCompile 'junit:junit:4.12'
            """
            dir('src/test/java').mkdirs()
            file 'src/test/java/MyTest.java', """
                public class MyTest {
                    public @org.junit.Test void test() { org.junit.Assert.assertTrue(true); }
                }
            """
        }
    }

    def "Job launches a Gradle test"() {
        setup:
        def job = new RunGradleJvmTestLaunchRequestJob(testTargets(), createRunConfigurationMock())

        when:
        job.schedule()
        job.join()

        then:
        job.getResult().isOK()
        buildOutput.contains ':test'
        buildOutput.contains 'BUILD SUCCESSFUL'
    }

    def "Job prints its configuration"() {
        setup:
        def job = new RunGradleJvmTestLaunchRequestJob(testTargets(), createRunConfigurationMock())

        when:
        job.schedule()
        job.join()

        then:
        job.getResult().isOK()
        buildConfig.contains 'Working Directory'
        buildConfig.contains 'Tests: MyTest'
    }

    RunConfiguration createRunConfigurationMock() {
        CorePlugin.configurationManager().loadRunConfiguration(createLaunchConfiguration(projectDir))
    }

    List<TestTarget> testTargets() {
        IType type = Mock(IType)
        type.elementName >> 'MyTest'
        type.fullyQualifiedName >> 'MyTest'
        [new TestType(type)]
    }
}
