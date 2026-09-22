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

import org.gradle.api.JavaVersion
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import spock.lang.AutoCleanup;
import spock.lang.IgnoreIf

import com.google.common.base.Optional
import org.eclipse.buildship.core.internal.launch.GradleLaunchConfigurationManager
import org.eclipse.buildship.ui.internal.UiPluginConstants
import org.eclipse.buildship.ui.internal.util.nodeselection.NodeSelection
import org.eclipse.buildship.ui.internal.test.fixtures.TestEnvironment
import org.eclipse.debug.core.ILaunchConfiguration
import org.eclipse.jface.viewers.StructuredSelection

@IgnoreIf({ JavaVersion.current().isJava9Compatible() }) // TODO update cglib and re-enable the test
class CreateAndOpenRunConfigurationActionTest extends ViewSpecification {

    CreateRunConfigurationAction createAction
    OpenRunConfigurationAction openAction

    def setup() {
        createAction = new CreateRunConfigurationAction(UiPluginConstants.OPEN_RUN_CONFIGURATION_COMMAND_ID, { NodeSelection.empty() })
        openAction = new OpenRunConfigurationAction(UiPluginConstants.OPEN_RUN_CONFIGURATION_COMMAND_ID, { NodeSelection.empty() })
    }

    def "No action is visible when the nothing is selected"() {
        setup:
        def selection = NodeSelection.empty()

        expect:
        !createAction.isVisibleFor(selection)
        !createAction.isEnabledFor(selection)
        !openAction.isVisibleFor(selection)
        !openAction.isEnabledFor(selection)
    }

    def "Only one action is visible and enabled when a task node is selected"(boolean runConfigurationAlreadyExists) {
        setup:
        environment.registerService(GradleLaunchConfigurationManager, newTestLaunchConfigurationManager(runConfigurationAlreadyExists))

        def rootNode = newProjectNode(null, '/root')
        def taskNode = newProjectTaskNode(rootNode, ':a')
        def selection = NodeSelection.from(new StructuredSelection(taskNode))

        expect:
        createAction.isVisibleFor(selection) != runConfigurationAlreadyExists
        createAction.isEnabledFor(selection) != runConfigurationAlreadyExists
        openAction.isVisibleFor(selection) == runConfigurationAlreadyExists
        openAction.isEnabledFor(selection) == runConfigurationAlreadyExists

        where:
        runConfigurationAlreadyExists << [true, false]
    }

    def "Only one action is visible and enabled when multiple task nodes are selected from the same project"(boolean runConfigurationAlreadyExists) {
        setup:
        environment.registerService(GradleLaunchConfigurationManager, newTestLaunchConfigurationManager(runConfigurationAlreadyExists))

        def rootNode = newProjectNode(null, '/root')
        def taskNode1 = newProjectTaskNode(rootNode, ':a')
        def taskNode2 = newProjectTaskNode(rootNode, ':b')
        def selection = NodeSelection.from(new StructuredSelection([taskNode1, taskNode2]))

        expect:
        createAction.isVisibleFor(selection) != runConfigurationAlreadyExists
        createAction.isEnabledFor(selection) != runConfigurationAlreadyExists
        openAction.isVisibleFor(selection) == runConfigurationAlreadyExists
        openAction.isEnabledFor(selection) == runConfigurationAlreadyExists

        where:
        runConfigurationAlreadyExists << [true, false]
    }

    def "Only one action is visible and enabled when a project node is selected"(boolean runConfigurationAlreadyExists) {
        setup:
        environment.registerService(GradleLaunchConfigurationManager, newTestLaunchConfigurationManager(runConfigurationAlreadyExists))

        def rootNode = newProjectNode(null, '/root')
        def selection = NodeSelection.from(new StructuredSelection(rootNode))

        expect:
        createAction.isVisibleFor(selection) != runConfigurationAlreadyExists
        createAction.isEnabledFor(selection) != runConfigurationAlreadyExists
        openAction.isVisibleFor(selection) == runConfigurationAlreadyExists
        openAction.isEnabledFor(selection) == runConfigurationAlreadyExists

        where:
        runConfigurationAlreadyExists << [true, false]
    }

    def "Create action is visible but disabled when multiple projects nodes are selected"(boolean runConfigurationAlreadyExists) {
        setup:
        environment.registerService(GradleLaunchConfigurationManager, newTestLaunchConfigurationManager(runConfigurationAlreadyExists))

        def rootNode = newProjectNode(null, '/root')
        def projectNode1 = newProjectNode(rootNode, '/root/a')
        def projectNode2 = newProjectNode(rootNode, '/root/b')
        def selection = NodeSelection.from(new StructuredSelection([projectNode1, projectNode2]))

        expect:
        createAction.isVisibleFor(selection)
        !createAction.isEnabledFor(selection)
        !openAction.isVisibleFor(selection)
        !openAction.isEnabledFor(selection)

        where:
        runConfigurationAlreadyExists << [true, false]
    }

    def "Create action is visible but disabled when task nodes from multiple projects are selected"(boolean runConfigurationAlreadyExists) {
        setup:
        environment.registerService(GradleLaunchConfigurationManager, newTestLaunchConfigurationManager(runConfigurationAlreadyExists))

        def rootNode = newProjectNode(null, '/root')
        def projectNode1 = newProjectNode(rootNode, '/root/a')
        def projectNode2 = newProjectNode(rootNode, '/root/b')
        def taskNode1 = newProjectTaskNode(projectNode1, ':a:one')
        def taskNode2 = newProjectTaskNode(projectNode2, ':b:two')
        def selection = NodeSelection.from(new StructuredSelection([taskNode1, taskNode2]))

        expect:
        createAction.isVisibleFor(selection)
        !createAction.isEnabledFor(selection)
        !openAction.isVisibleFor(selection)
        !openAction.isEnabledFor(selection)

        where:
        runConfigurationAlreadyExists << [true, false]
    }

    def "Create action is visible but disabled when task selectors on a non-standard flat sub-project are selected"() {
        setup:
        environment.registerService(GradleLaunchConfigurationManager, newTestLaunchConfigurationManager(false))

        def rootNode = newProjectNode(null, '/root')
        def subProjectNode = newProjectNode(rootNode, '/sub')
        def taskSelectorNode = newTaskSelectorNode(subProjectNode)
        def selection = NodeSelection.from(new StructuredSelection(taskSelectorNode))

        expect:
        createAction.isVisibleFor(selection)
        !createAction.isEnabledFor(selection)
    }

    def "Create action is visible and enabled when task selectors on a standard flat sub-project are selected"() {
        setup:
        environment.registerService(GradleLaunchConfigurationManager, newTestLaunchConfigurationManager(false))

        def rootNode = newProjectNode(null, '/master')
        def subProjectNode = newProjectNode(rootNode, '/sub')
        def taskSelectorNode = newTaskSelectorNode(subProjectNode)
        def selection = NodeSelection.from(new StructuredSelection(taskSelectorNode))

        expect:
        createAction.isVisibleFor(selection)
        createAction.isEnabledFor(selection)
    }

    def "Create action is visible and enabled when project tasks on a non-standard flat sub-project are selected"() {
        setup:
        environment.registerService(GradleLaunchConfigurationManager, newTestLaunchConfigurationManager(false))

        def rootNode = newProjectNode(null, '/root')
        def subProjectNode = newProjectNode(rootNode, '/sub')
        def taskNode = newProjectTaskNode(subProjectNode, ':sub:foo')
        def selection = NodeSelection.from(new StructuredSelection(taskNode))

        expect:
        createAction.isVisibleFor(selection)
        createAction.isEnabledFor(selection)
    }

    def "No action is visible nor enabled when a project and tasks from multiple projects are selected"(boolean runConfigurationAlreadyExists) {
        setup:
        environment.registerService(GradleLaunchConfigurationManager, newTestLaunchConfigurationManager(runConfigurationAlreadyExists))

        def rootNode = newProjectNode(null, '/root')
        def taskNode = newProjectTaskNode(rootNode, ':a:one')
        def selection = NodeSelection.from(new StructuredSelection([rootNode, taskNode]))

        expect:
        !createAction.isVisibleFor(selection)
        !createAction.isEnabledFor(selection)
        !openAction.isVisibleFor(selection)
        !openAction.isEnabledFor(selection)

        where:
        runConfigurationAlreadyExists << [true, false]
    }

    def newTestLaunchConfigurationManager(boolean runConfigurationAlwaysExist) {
        GradleLaunchConfigurationManager manager = Mock(GradleLaunchConfigurationManager)
        if (runConfigurationAlwaysExist) {
            manager.getRunConfiguration(_) >> Optional.of(Mock(ILaunchConfiguration))
        } else {
            manager.getRunConfiguration(_) >> Optional.absent()
        }
        manager
    }

}
