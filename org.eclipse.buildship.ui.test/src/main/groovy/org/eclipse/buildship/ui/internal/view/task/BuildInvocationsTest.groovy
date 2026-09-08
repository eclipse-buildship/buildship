/*******************************************************************************
 * Copyright (c) 2023 Gradle Inc. and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.ui.internal.view.task

import org.gradle.tooling.GradleConnector
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.eclipse.EclipseProject

import org.eclipse.core.resources.IProject
import org.eclipse.core.runtime.NullProgressMonitor

import org.eclipse.buildship.core.internal.CorePlugin
import org.eclipse.buildship.core.internal.util.gradle.Path
import org.eclipse.buildship.core.internal.workspace.FetchStrategy
import org.eclipse.buildship.core.internal.workspace.ModelProvider
import org.eclipse.buildship.ui.internal.test.fixtures.ProjectSynchronizationSpecification

class BuildInvocationsTest extends ProjectSynchronizationSpecification {

    def "Project with subtasks has valid project tasks and task selectors"() {
        setup:
        File projectDir = dir('project-with-subtasks') {
            file 'build.gradle', '''
                task alpha {
                  description = 'ALPHA'
                  group = 'build'
                }
                task beta {}
            '''

            file'settings.gradle', '''
                include 'sub1'
                include 'sub2'
                include 'sub2:subSub'
            '''

            dir('sub1') {
                file 'build.gradle', '''
                    task beta {
                        description = 'BETA'
                        group = 'build'
                    }
                    task gamma {}
                    task epsilon {}
                '''
            }

            dir('sub2') {
                file 'build.gradle', '''
                   task beta {}
                   task delta { description = 'DELTA' }
                   task epsilon { group = 'build' }
                '''
                dir('subSub') {
                    file 'build.gradle', '''
                        task alpha { description = 'ALPHA_SUB2' }
                        task delta { description = 'DELTA_SUB2' }
                        task zeta {}
                    '''
                }
            }
        }

        when:
        importAndWait(projectDir)
        IProject project = findProject('project-with-subtasks')

        ModelProvider modelProvider = CorePlugin.internalGradleWorkspace().getBuild(project).get().modelProvider
        Map<String, EclipseProject> eclipseProjects = modelProvider.fetchModels(EclipseProject, FetchStrategy.LOAD_IF_NOT_CACHED, GradleConnector.newCancellationTokenSource(), new NullProgressMonitor())
        GradleProject gradleProject = eclipseProjects[':'].gradleProject
        Map<Path, BuildInvocations> pathToBuildInvocations = BuildInvocations.collectAll(gradleProject)

        then:
        pathToBuildInvocations.keySet() == [Path.from(':'), Path.from(':sub1'), Path.from(':sub2'), Path.from(':sub2:subSub')] as Set

        and:
        BuildInvocations invocationsAtRoot = pathToBuildInvocations.get(Path.from(':'))
        collectNamesOfNonImplicitTaskSelectors(invocationsAtRoot.taskSelectors) == ['alpha', 'beta', 'gamma', 'delta', 'epsilon', 'zeta'] as Set
        assertTaskSelector('alpha', 'ALPHA', true, "build", [':alpha', ':sub2:subSub:alpha'], invocationsAtRoot.taskSelectors)
        assertTaskSelector('beta', '', true, 'other', [':beta', ':sub1:beta', ':sub2:beta'], invocationsAtRoot.taskSelectors)
        assertTaskSelector('gamma', '', false, 'other', [':sub1:gamma'], invocationsAtRoot.taskSelectors)
        assertTaskSelector('delta', 'DELTA', false, 'other', [':sub2:delta', ':sub2:subSub:delta'], invocationsAtRoot.taskSelectors)
        assertTaskSelector('epsilon', '', true, 'other', [':sub1:epsilon', ':sub2:epsilon'], invocationsAtRoot.taskSelectors)
        assertTaskSelector('zeta', '', false, 'other', [':sub2:subSub:zeta'], invocationsAtRoot.taskSelectors)

        and:
        collectNamesOfNonImplicitProjectTasks(invocationsAtRoot.projectTasks) == ['alpha', 'beta'] as Set
        assertTask('alpha', 'ALPHA', true, ':alpha', 'build', invocationsAtRoot.projectTasks)
        assertTask('beta', '', false, ':beta', 'other', invocationsAtRoot.projectTasks)

        and:
        BuildInvocations invocationsAtSub1 = pathToBuildInvocations.get(Path.from(':sub1'))
        collectNamesOfNonImplicitTaskSelectors(invocationsAtSub1.taskSelectors) == ['beta', 'gamma', 'epsilon'] as Set
        assertTaskSelector('beta', 'BETA', true, "build", [':sub1:beta'], invocationsAtSub1.taskSelectors)
        assertTaskSelector('gamma', '', false, 'other', [':sub1:gamma'], invocationsAtSub1.taskSelectors)
        assertTaskSelector('epsilon', '', false, 'other', [':sub1:epsilon'], invocationsAtSub1.taskSelectors)

        and:
        collectNamesOfNonImplicitProjectTasks(invocationsAtSub1.projectTasks) == ['beta', 'gamma', 'epsilon'] as Set
        assertTask('beta', 'BETA', true, ':sub1:beta', "build", invocationsAtSub1.projectTasks)
        assertTask('gamma', '', false, ':sub1:gamma', 'other', invocationsAtSub1.projectTasks)
        assertTask('epsilon', '', false, ':sub1:epsilon', 'other', invocationsAtSub1.projectTasks)

        and:
        BuildInvocations invocationsAtSub2 = pathToBuildInvocations.get(Path.from(':sub2'))
        collectNamesOfNonImplicitTaskSelectors(invocationsAtSub2.taskSelectors) == ['alpha', 'beta', 'delta', 'epsilon', 'zeta'] as Set
        assertTaskSelector('alpha', 'ALPHA_SUB2', false, 'other', [':sub2:subSub:alpha'], invocationsAtSub2.taskSelectors)
        assertTaskSelector('beta', '', false, 'other', [':sub2:beta'], invocationsAtSub2.taskSelectors)
        assertTaskSelector('delta', 'DELTA', false, 'other', [':sub2:delta', ':sub2:subSub:delta'], invocationsAtSub2.taskSelectors)
        assertTaskSelector('epsilon', '', true, 'build', [':sub2:epsilon'], invocationsAtSub2.taskSelectors)
        assertTaskSelector('zeta', '', false, 'other', [':sub2:subSub:zeta'], invocationsAtSub2.taskSelectors)

        and:
        collectNamesOfNonImplicitProjectTasks(invocationsAtSub2.projectTasks) == ['beta', 'delta', 'epsilon'] as Set
        assertTask('beta', '', false, ':sub2:beta', 'other', invocationsAtSub2.projectTasks)
        assertTask('delta', 'DELTA', false, ':sub2:delta', 'other', invocationsAtSub2.projectTasks)
        assertTask('epsilon', '', true, ':sub2:epsilon', 'build', invocationsAtSub2.projectTasks)

        and:
        BuildInvocations invocationsAtSubSub = pathToBuildInvocations.get(Path.from(':sub2:subSub'))
        collectNamesOfNonImplicitTaskSelectors(invocationsAtSubSub.taskSelectors) == ['alpha', 'delta', 'zeta'] as Set
        assertTaskSelector('alpha', 'ALPHA_SUB2', false, 'other', [':sub2:subSub:alpha'], invocationsAtSubSub.taskSelectors)
        assertTaskSelector('delta', 'DELTA_SUB2', false, 'other', [':sub2:subSub:delta'], invocationsAtSubSub.taskSelectors)
        assertTaskSelector('zeta', '', false, 'other', [':sub2:subSub:zeta'], invocationsAtSubSub.taskSelectors)

        and:
        collectNamesOfNonImplicitProjectTasks(invocationsAtSubSub.projectTasks) == ['alpha', 'delta', 'zeta'] as Set
        assertTask('alpha', 'ALPHA_SUB2', false, ':sub2:subSub:alpha', 'other', invocationsAtSubSub.projectTasks)
        assertTask('delta', 'DELTA_SUB2', false, ':sub2:subSub:delta', 'other', invocationsAtSubSub.projectTasks)
        assertTask('zeta', '', false, ':sub2:subSub:zeta', 'other', invocationsAtSubSub.projectTasks)
    }

    def "Empty multi-project build has no tasks"() {
        setup:
        File projectDir = dir('project-without-tasks') {
            file'settings.gradle', '''
                include 'sub1'
                include 'sub2'
            '''
            dir('sub1')
            dir('sub2')
        }

        when:
        importAndWait(projectDir)

        IProject project = findProject('project-without-tasks')
        ModelProvider modelProvider = CorePlugin.internalGradleWorkspace().getBuild(project).get().modelProvider
        Map<String, EclipseProject> eclipseProjects = modelProvider.fetchModels(EclipseProject, FetchStrategy.LOAD_IF_NOT_CACHED, GradleConnector.newCancellationTokenSource(), new NullProgressMonitor())
        GradleProject gradleProject = eclipseProjects[':'].gradleProject
        Map<Path, BuildInvocations> pathToBuildInvocations = BuildInvocations.collectAll(gradleProject)

        then:
        pathToBuildInvocations.keySet() == [Path.from(':'), Path.from(':sub1'), Path.from(':sub2')] as Set
        pathToBuildInvocations.each { Path path, BuildInvocations buildInvocation ->
            assert buildInvocation.projectTasks.collect { it.name } - implicitTasks == []
            assert buildInvocation.taskSelectors.collect { it.name } - implicitTasks == []
        }
  }

    private static Set<String> collectNamesOfNonImplicitTaskSelectors(List<TaskSelector> tasks) {
        tasks.collect { it.name }.findAll { !implicitTasks.contains(it) } as Set
    }

    private static Set<String> getImplicitTasks() {
        [
            "init",
            "wrapper",
            "help",
            "projects",
            "tasks",
            "properties",
            "components",
             "dependencies",
             "dependencyInsight",
             "setupBuild",
             "model",
             "buildEnvironment",
             "dependentComponents",
             "nothing",
             "prepareKotlinBuildScriptModel",
             "cleanEclipse",
             "cleanEclipseProject",
             "eclipse",
             "eclipseProject",
             "outgoingVariants",
             "buildScanPublishPrevious",
             "javaToolchains",
             "provisionGradleEnterpriseAccessKey",
             "resolvableConfigurations",
             "updateDaemonJvm"
         ]
    }

    private static Set<String> collectNamesOfNonImplicitProjectTasks(List<ProjectTask> tasks) {
        tasks.collect { it.name }.findAll { !implicitTasks.contains(it) } as Set
    }

    private static void assertTaskSelector(String name, String description, boolean isPublic, String group, List<String> taskNames, List<TaskSelector> selectors) {
        TaskSelector element = selectors.find { it.name == name }
        assert element != null
        assert element.name == name
        assert element.description == description
        assert element.public == isPublic
        assert element.group == group

        assert element.selectedTaskPaths*.path as List == taskNames
    }

    private static void assertTask(String name, String description, boolean isPublic, String path, String group, List<ProjectTask> tasks) {
        ProjectTask element = tasks.find { it.name == name }
        assert element != null
        assert element.name == name
        assert element.description == description
        assert element.isPublic() == isPublic
        assert element.group == group
        assert element.path.path == path
    }
}
