/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Etienne Studer & Donát Csikós (Gradle Inc.) - initial API and implementation and initial documentation
 */

package org.eclipse.buildship.core.launch

import spock.lang.Shared

import org.eclipse.debug.core.ILaunchConfiguration

import org.eclipse.buildship.core.GradleDistribution
import org.eclipse.buildship.core.GradleDistributionInfo
import org.eclipse.buildship.core.GradlePluginsRuntimeException
import org.eclipse.buildship.core.test.fixtures.WorkspaceSpecification

class GradleRunConfigurationAttributesTest extends WorkspaceSpecification {

    @Shared def Attributes validAttributes = new Attributes (
        tasks : ['clean'],
        workingDir : "/home/user/workspace",
        gradleDistr : GradleDistribution.fromBuild().distributionInfo.serializeToString(),
        gradleUserHome: '/.gradlehome',
        javaHome : "/.java",
        arguments : ["-q"],
        jvmArguments : ["-ea"],
        showExecutionView :  true,
        showConsoleView : true,
        overrideBuildSettings : false,
        isOffline: false,
        buildScansEnabled: false,
    )

    def "Can create an instance from empty run configuration"() {
        setup:
        ILaunchConfiguration eclipseConfig = createGradleLaunchConfig()

        when:
        GradleRunConfigurationAttributes attributes = GradleRunConfigurationAttributes.from(eclipseConfig)

        then:
        attributes.tasks == []
        attributes.workingDir.absolutePath == new File('').absolutePath
        attributes.gradleDistribution == GradleDistribution.fromBuild()
        attributes.gradleUserHome == null
        attributes.javaHome == null
        attributes.arguments == []
        attributes.jvmArguments == []
        attributes.showExecutionView == true
        attributes.showConsoleView == true
        attributes.overrideBuildSettings == false
        attributes.isOffline == false
        attributes.buildScansEnabled ==false
    }

    def "Can create a new valid instance"() {
        when:
        def configuration = validAttributes.toConfiguration()

        then:
        // not null
        configuration != null
        // check non-calculated values
        configuration.getTasks() == validAttributes.tasks
        configuration.getWorkingDirExpression() == validAttributes.workingDir
        configuration.getJavaHomeExpression() == validAttributes.javaHome
        configuration.getJvmArguments() == validAttributes.jvmArguments
        configuration.getArguments() == validAttributes.arguments
        configuration.isShowExecutionView() == validAttributes.showExecutionView
        configuration.isShowConsoleView() == validAttributes.showConsoleView
        configuration.isOverrideBuildSettings() == validAttributes.overrideBuildSettings
        configuration.isOffline() == validAttributes.isOffline
        configuration.isBuildScansEnabled() == validAttributes.buildScansEnabled
        // check calculated value
        configuration.getArgumentExpressions() == validAttributes.arguments
        configuration.getJvmArgumentExpressions() == validAttributes.jvmArguments
        configuration.getWorkingDir().getAbsolutePath() == new File(validAttributes.workingDir).getAbsolutePath()
        configuration.getJavaHome().getAbsolutePath() == new File(validAttributes.javaHome).getAbsolutePath()
        configuration.getGradleDistribution() == GradleDistributionInfo.deserializeFromString(validAttributes.gradleDistr).toGradleDistribution()
        configuration.getGradleUserHome().getAbsolutePath() == new File(validAttributes.gradleUserHome).getAbsolutePath()
    }

    def "Can create a new valid instance with valid null arguments"(Attributes attributes) {
        when:
        def configuration = attributes.toConfiguration()

        then:
        configuration != null
        attributes.javaHome != null || configuration.getJavaHome() == null

        where:
        attributes << [
            validAttributes.copy { javaHome = null },
        ]
    }

    def "Creation fails when null argument passed"(Attributes attributes) {
        when:
        attributes.toConfiguration()

        then:
        thrown(RuntimeException)

        where:
        attributes << [
            validAttributes.copy { tasks = null },
            validAttributes.copy { workingDir = null },
            validAttributes.copy { jvmArguments = null},
            validAttributes.copy { arguments = null}
        ]
    }

    def "Expressions can be resolved in the parameters"() {
        when:
        def Attributes attributes = validAttributes.copy {
            workingDir = '${workspace_loc}/working_dir'
            javaHome = '${workspace_loc}/java_home'
        }
        def configuration = attributes.toConfiguration()

        then:
        configuration.getWorkingDir().getPath().endsWith("working_dir")
        !(configuration.getWorkingDir().getPath().contains('$'))
        configuration.getJavaHome().getPath().endsWith("java_home")
        !(configuration.getJavaHome().getPath().contains('$'))
    }

    def "Unresolvable expressions in Java home results in runtime exception"() {
        setup:
        def Attributes attributes = validAttributes.copy {
            javaHome = '${nonexistingvariable}/java_home'
        }
        def configuration = attributes.toConfiguration()

        when:
        configuration.getJavaHome()

        then:
        thrown(GradlePluginsRuntimeException)

    }

    def "Unresolvable expressions in working directory results in runtime exception"() {
        setup:
        def Attributes attributes = validAttributes.copy {
            workingDir = '${nonexistingvariable}/working_dir'
        }
        def configuration = attributes.toConfiguration()

        when:
        configuration.getWorkingDir()

        then:
        thrown(GradlePluginsRuntimeException)
    }

    def "Unresolvable expressions in arguments results in runtime exception"() {
        setup:
        def Attributes attributes = validAttributes.copy {
            arguments = ['${nonexistingvariable}/arguments']
        }
        def configuration = attributes.toConfiguration()

        when:
        configuration.getArguments()

        then:
        thrown(GradlePluginsRuntimeException)
    }

    def "Unresolvable expressions in jvm arguments results in runtime exception"() {
        setup:
        def Attributes attributes = validAttributes.copy {
            jvmArguments = ['${nonexistingvariable}/jvmarguments']
        }
        def configuration = attributes.toConfiguration()

        when:
        configuration.getJvmArguments()

        then:
        thrown(GradlePluginsRuntimeException)
    }

    def "All configuration can be saved to Eclipse settings"() {
        setup:
        ILaunchConfiguration eclipseConfig = createGradleLaunchConfig()

        when:
        assert eclipseConfig.getAttributes().isEmpty()
        def gradleConfig = validAttributes.toConfiguration()
        gradleConfig.apply(eclipseConfig)

        then:
        eclipseConfig.getAttributes().size() == validAttributes.size()
    }

    def "All valid configuration settings can be stored and retrieved"(Attributes attributes) {
        setup:
        ILaunchConfiguration eclipseConfig = createGradleLaunchConfig()

        when:
        def gradleConfig1 = attributes.toConfiguration()
        gradleConfig1.apply(eclipseConfig)
        def gradleConfig2 = GradleRunConfigurationAttributes.from(eclipseConfig)

        then:
        gradleConfig1.getTasks() == gradleConfig2.getTasks()
        gradleConfig1.getWorkingDirExpression() == gradleConfig2.getWorkingDirExpression()
        gradleConfig1.getGradleDistribution() == gradleConfig2.getGradleDistribution()
        gradleConfig1.getGradleUserHome() == gradleConfig2.getGradleUserHome()
        gradleConfig1.getJavaHomeExpression() == gradleConfig2.getJavaHomeExpression()
        gradleConfig1.getJvmArguments() == gradleConfig2.getJvmArguments()
        gradleConfig1.getArguments() == gradleConfig2.getArguments()
        gradleConfig1.isShowExecutionView() == gradleConfig2.isShowExecutionView()
        gradleConfig1.isOverrideBuildSettings() == gradleConfig2.isOverrideBuildSettings()
        gradleConfig1.isOffline() == gradleConfig2.isOffline()
        gradleConfig1.isBuildScansEnabled() == gradleConfig2.isBuildScansEnabled()

        where:
        attributes << [
            validAttributes,
            validAttributes.copy { javaHome = null },
        ]
    }

    def "Saved Configuration attributes has same unique attributes"() {
        setup:
        ILaunchConfiguration eclipseConfig = createGradleLaunchConfig()

        when:
        def gradleConfig = validAttributes.toConfiguration()
        gradleConfig.apply(eclipseConfig)

        then:
        gradleConfig.hasSameUniqueAttributes(eclipseConfig)
    }

    static class Attributes implements Cloneable {
        def tasks
        def workingDir
        def gradleDistr
        def gradleUserHome
        def javaHome
        def arguments
        def jvmArguments
        def showExecutionView
        def showConsoleView
        def overrideBuildSettings
        def isOffline
        def buildScansEnabled

        def GradleRunConfigurationAttributes toConfiguration() {
            new GradleRunConfigurationAttributes(tasks, workingDir, gradleDistr, gradleUserHome, javaHome, jvmArguments, arguments, showExecutionView, showConsoleView, overrideBuildSettings, isOffline, buildScansEnabled)
        }

        def Attributes copy(@DelegatesTo(value = Attributes, strategy=Closure.DELEGATE_FIRST) Closure closure) {
            def clone = clone()
            def Closure clonedClosure =  closure.clone()
            clonedClosure.setResolveStrategy(Closure.DELEGATE_FIRST)
            clonedClosure.setDelegate(clone)
            clonedClosure.call(clone)
            return clone
        }

        def int size() {
            Attributes.declaredFields.findAll { it.synthetic == false }.size
        }
    }

}
