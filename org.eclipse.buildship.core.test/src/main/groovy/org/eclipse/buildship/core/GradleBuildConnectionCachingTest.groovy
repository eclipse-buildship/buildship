/*******************************************************************************
 * Copyright (c) 2023 Gradle Inc. and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.core

import static org.gradle.api.JavaVersion.VERSION_13

import java.util.function.Function

import org.gradle.api.JavaVersion
import org.gradle.tooling.IntermediateResultHandler
import org.gradle.tooling.ModelBuilder
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.eclipse.EclipseRuntime
import spock.lang.IgnoreIf
import spock.lang.Unroll

import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.NullProgressMonitor

import org.eclipse.buildship.core.internal.CorePlugin
import org.eclipse.buildship.core.internal.console.ProcessStreamsProvider
import org.eclipse.buildship.core.internal.test.fixtures.TestProcessStreamProvider
import org.eclipse.buildship.core.internal.util.gradle.IdeFriendlyClassLoading
import org.eclipse.buildship.core.internal.workspace.EclipseModelUtils

class GradleBuildConnectionCachingTest extends BaseProjectConfiguratorTest {

    File location

    def setup() {
        location = dir('GradleBuildConnectionCachingTest') { file "build.gradle",
            """
                plugins {
                    id 'java'
                }

                System.out.println "ScriptExecuted"
            """
        }
    }

    def "Does not cache results when not in synchronization"() {
        setup:
        GradleBuild gradleBuild = gradleBuildFor(location)
        Function query = { ProjectConnection c -> c.getModel(GradleProject) }

        when:
        GradleProject firstModel = gradleBuild.withConnection(query, null)
        GradleProject secondModel = gradleBuild.withConnection(query, null)

        then:

        !firstModel.is(secondModel)
        assertModelLoadedTwice()
    }

    def "Model query loads result from cache during synchronization"() {
        setup:
        TestConfigurator firstConfigurator = new TestConfigurator({ ProjectConnection p -> p.getModel(EclipseProject.class) })
        TestConfigurator secondConfigurator = new TestConfigurator({ ProjectConnection p -> p.model(EclipseProject.class).get() })
        ResultHandler<EclipseProject> resultHandler = Mock(ResultHandler)
        TestConfigurator thirdConfigurator = new TestConfigurator({ ProjectConnection p -> p.model(EclipseProject.class).get(resultHandler) })
        registerConfigurator(firstConfigurator)
        registerConfigurator(secondConfigurator)
        registerConfigurator(thirdConfigurator)
        GradleBuild gradleBuild = gradleBuildFor(location)

        when:
        gradleBuild.synchronize(new NullProgressMonitor())

        then:
        firstConfigurator.result.is(secondConfigurator.result)
        1 * resultHandler.onComplete({ it.is(firstConfigurator.result) })
        // synchronization loads EclipseProject with CompositeModelQuery build action
        // first configurator loads EclipseProject with ProjectConnection.getModel
        // second  and third configurator loads EclipseProject from the cache
        assertModelLoadedTwice()
    }

    @IgnoreIf({ JavaVersion.current().isCompatibleWith(VERSION_13) }) // Gradle 5.4 can run on Java 12 and below
    def "Build action loads value from cache during synchronization"() {
        setup:
        ResultHandler<Collection<EclipseProject>> resultHandler = Mock(ResultHandler)
        Function<ProjectConnection, EclipseProject> firstAction = { ProjectConnection p -> p.action(IdeFriendlyClassLoading.loadCompositeModelQuery(EclipseProject.class)).run() }
        Function<ProjectConnection, EclipseProject> secondAction = { ProjectConnection p -> p.action(IdeFriendlyClassLoading.loadCompositeModelQuery(EclipseProject.class)).run(resultHandler) }
        TestConfigurator firstConfigurator = new TestConfigurator(firstAction)
        TestConfigurator secondConfigurator = new TestConfigurator(secondAction)
        registerConfigurator(firstConfigurator)
        registerConfigurator(secondConfigurator)
        GradleBuild gradleBuild = gradleBuildFor(location, GradleDistribution.forVersion("5.4.1"))

        when:
        gradleBuild.synchronize(new NullProgressMonitor())

        then:
        1 * resultHandler.onComplete({ it.is(firstConfigurator.result) })
        // synchronization also uses composite model query, therefore both configurators get cache hits
        assertModelLoadedOnce()
    }

    def "Build action loads value from cache during synchronization while supplying eclipseRuntime"() {
        setup:
        ResultHandler<Collection<EclipseProject>> resultHandler = Mock(ResultHandler)
        Function<ProjectConnection, EclipseProject> firstAction = { ProjectConnection p -> p.action(IdeFriendlyClassLoading.loadCompositeModelQuery(EclipseProject.class, EclipseRuntime.class, EclipseModelUtils.buildEclipseRuntimeConfigurer())).run() }
        Function<ProjectConnection, EclipseProject> secondAction = { ProjectConnection p -> p.action(IdeFriendlyClassLoading.loadCompositeModelQuery(EclipseProject.class, EclipseRuntime.class, EclipseModelUtils.buildEclipseRuntimeConfigurer())).run(resultHandler) }
        TestConfigurator firstConfigurator = new TestConfigurator(firstAction)
        TestConfigurator secondConfigurator = new TestConfigurator(secondAction)
        registerConfigurator(firstConfigurator)
        registerConfigurator(secondConfigurator)
        GradleBuild gradleBuild = gradleBuildFor(location)

        when:
        gradleBuild.synchronize(new NullProgressMonitor())

        then:
        1 * resultHandler.onComplete({ it.is(firstConfigurator.result) })
        // synchronization also uses composite model query, therefore both configurators get cache hits
        assertModelLoadedOnce()
    }

    def "Result loaded from the build cache if same properties are specified"() {
        setup:
        List<String> tasks = ['projects']
        Map<String, String> envVars = ['envKey' : 'envVal']
        List<String> arguments = ['-PargKey=argvalue']
        List<String> jvmArguments = ['-DjvmArgKey=jvmArgValue']
        File javaHome = new File(System.getProperty('java.home'))

        TestConfigurator firstConfigurator = new TestConfigurator({ ProjectConnection p -> p.model(EclipseProject.class).forTasks(tasks).setEnvironmentVariables(envVars).withArguments(arguments).setJvmArguments(jvmArguments).setJavaHome(javaHome).get() })
        TestConfigurator secondConfigurator = new TestConfigurator({ ProjectConnection p -> p.model(EclipseProject.class).forTasks(tasks).setEnvironmentVariables(envVars).withArguments(arguments).setJvmArguments(jvmArguments).setJavaHome(javaHome).get() })
        registerConfigurator(firstConfigurator)
        registerConfigurator(secondConfigurator)
        GradleBuild gradleBuild = gradleBuildFor(location)

        when:
        gradleBuild.synchronize(new NullProgressMonitor())

        then:
        assertModelLoadedTimes(2)
    }

    @Unroll
    def "Result not loaded from the build cache if different #propertyName are specified"(String propertyName, Closure function) {
        setup:
        TestConfigurator firstConfigurator = new TestConfigurator({ ProjectConnection p -> def modelBuilder = p.model(EclipseProject.class); function(modelBuilder); modelBuilder.get() })
        TestConfigurator secondConfigurator = new TestConfigurator({ ProjectConnection p -> p.model(EclipseProject.class).get() })
        registerConfigurator(firstConfigurator)
        registerConfigurator(secondConfigurator)
        GradleBuild gradleBuild = gradleBuildFor(location)

        when:
        gradleBuild.synchronize(new NullProgressMonitor())

        then:
        assertModelLoadedTimes(3)

        where:
        propertyName              | function
        'tasks'                   | { ModelBuilder b -> b.forTasks(['projects']) }
        'environmental variables' | { ModelBuilder b -> b.setEnvironmentVariables(['envKey' : 'envVal']) }
        'arguments'               | { ModelBuilder b -> b.addArguments(['-PargKey=argvalue']) }
        'JVM arguments'           | { ModelBuilder b -> b.setJvmArguments(['-DjvmArgKey=jvmArgValue']) }
        'Java home'               | { ModelBuilder b -> b.setJavaHome(new File(System.getProperty('java.home'))) }
    }

    @Unroll
    def "If client specifies custom #propertyName then the cache is disabled"(String propertyName, Closure function) {
        setup:
        TestConfigurator firstConfigurator = new TestConfigurator({ ProjectConnection p -> def modelBuilder = p.model(EclipseProject.class); function(modelBuilder); modelBuilder.get() })
        TestConfigurator secondConfigurator = new TestConfigurator({ ProjectConnection p -> def modelBuilder = p.model(EclipseProject.class); function(modelBuilder); modelBuilder.get() })
        registerConfigurator(firstConfigurator)
        registerConfigurator(secondConfigurator)
        GradleBuild gradleBuild = gradleBuildFor(location)

        when:
        gradleBuild.synchronize(new NullProgressMonitor())

        then:
        assertModelLoadedTimes(3)

        where:
        propertyName                 | function
        'old progress listeners'     | { ModelBuilder b -> b.addProgressListener(Mock(org.gradle.tooling.ProgressListener)) }
        'new progress listeners (1)' | { ModelBuilder b -> b.addProgressListener(Mock(org.gradle.tooling.events.ProgressListener)) }
        'new progress listeners (2)' | { ModelBuilder b -> b.addProgressListener(Mock(org.gradle.tooling.events.ProgressListener), OperationType.TASK) }
        'new progress listeners (3)' | { ModelBuilder b -> b.addProgressListener(Mock(org.gradle.tooling.events.ProgressListener), [OperationType.TASK] as Set) }
        'color output'               | { ModelBuilder b -> b.setColorOutput(false) }
        'standard in'                | { ModelBuilder b -> b.setStandardInput(CorePlugin.processStreamsProvider().backgroundJobProcessStreams.input) }
        'standard out'               | { ModelBuilder b -> b.setStandardOutput(CorePlugin.processStreamsProvider().backgroundJobProcessStreams.output) }
        'standard error'             | { ModelBuilder b -> b.setStandardError(CorePlugin.processStreamsProvider().backgroundJobProcessStreams.error) }
    }

    def "Result cached when model loaded via BuildActionExecuter.Builder"() {
        setup:
        TestConfigurator firstConfigurator = new TestConfigurator({ ProjectConnection p ->
            p.action().buildFinished(IdeFriendlyClassLoading.loadCompositeModelQuery(EclipseProject.class), {} as IntermediateResultHandler).build().forTasks().run()
        })
        TestConfigurator secondConfigurator = new TestConfigurator({ ProjectConnection p ->
            p.action(IdeFriendlyClassLoading.loadCompositeModelQuery(EclipseProject.class)).run()
        })
        registerConfigurator(firstConfigurator)
        registerConfigurator(secondConfigurator)

        GradleBuild gradleBuild = gradleBuildFor(location)

        when:
        gradleBuild.synchronize(new NullProgressMonitor())

        then:
        assertModelLoadedTwice()

    }

    private void assertModelLoadedOnce() {
        assertModelLoadedTimes(1)
    }

    private void assertModelLoadedTwice() {
        assertModelLoadedTimes(2)
    }

    private void assertModelLoadedTimes(int times) {
        String out = CorePlugin.processStreamsProvider().backgroundJobProcessStreams.out
        assert out.count('ScriptExecuted') == times
    }

    static class TestConfigurator<T> implements ProjectConfigurator {

        Function<ProjectConnection, EclipseProject> action
        T result

        TestConfigurator(Function<ProjectConnection, EclipseProject> action) {
            this.action = action
        }

        @Override
        public void init(InitializationContext context, IProgressMonitor monitor) {
            result = context.gradleBuild.withConnection(action, monitor)
        }

        @Override
        public void configure(ProjectContext context, IProgressMonitor monitor) { }

        @Override
        public void unconfigure(ProjectContext context, IProgressMonitor monitor) { }
    }
}
