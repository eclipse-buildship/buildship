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

import org.eclipse.buildship.ui.internal.UiPluginConstants
import org.eclipse.buildship.ui.internal.util.nodeselection.NodeSelection

import org.gradle.api.JavaVersion
import spock.lang.IgnoreIf

import org.eclipse.jface.viewers.StructuredSelection

@IgnoreIf({ JavaVersion.current().isJava9Compatible() }) // TODO update cglib and re-enable the test
class OpenBuildScriptActionTest extends ViewSpecification {

  OpenBuildScriptAction openAction

  def setup() {
    openAction = new OpenBuildScriptAction(UiPluginConstants.OPEN_BUILD_SCRIPT_COMMAND_ID, { NodeSelection.empty() })
  }

  def "Action is not visible nor enabled when nothing is selected"() {
    setup:
    def selection = NodeSelection.empty()

    expect:
    !openAction.isVisibleFor(selection)
    !openAction.isEnabledFor(selection)
  }

  def "Action visible and enabled when a single project node is selected"() {
    setup:
    def projectNode = newProjectNode(null, '/location')
    def selection = NodeSelection.from(new StructuredSelection(projectNode))

    expect:
    openAction.isVisibleFor(selection)
    openAction.isEnabledFor(selection)
  }

  def "Action visible and enabled when multiple projects are selected"() {
    setup:
    def projectNode1 = newProjectNode(null, '/location1')
    def projectNode2 = newProjectNode(null, '/location2')
    def selection = NodeSelection.from(new StructuredSelection([projectNode1, projectNode2]))

    expect:
    openAction.isVisibleFor(selection)
    openAction.isEnabledFor(selection)
  }

  def "Action is not visible nor enabled when a task node is selected"() {
    setup:
    def taskNode = newProjectTaskNode(newProjectNode(null, '/location1'), ':path')
    def selection = NodeSelection.from(new StructuredSelection(taskNode))

    expect:
    !openAction.isVisibleFor(selection)
    !openAction.isEnabledFor(selection)
  }

  def "Action is not visible nor enabled when a project and a task node is selected"() {
    setup:
    def projectNode = newProjectNode(null, '/location1')
    def taskNode = newProjectTaskNode(projectNode, ':path')
    def selection = NodeSelection.from(new StructuredSelection([projectNode, taskNode]))

    expect:
    !openAction.isVisibleFor(selection)
    !openAction.isEnabledFor(selection)
  }

}
