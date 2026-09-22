/*******************************************************************************
 * Copyright (c) 2026 Gradle Inc. and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.ui.internal.view.task

import org.eclipse.jface.viewers.DoubleClickEvent
import org.eclipse.swt.widgets.Event
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem
import org.eclipse.ui.IWorkbenchPage
import org.eclipse.ui.PlatformUI

import org.eclipse.buildship.ui.internal.UiPluginConstants
import org.eclipse.buildship.ui.internal.util.workbench.WorkbenchUtils

/**
 * Covers https://github.com/eclipse-buildship/buildship/issues/1340: running tasks from the task
 * view must not depend on the task view being the active workbench part.
 */
class TaskViewExecutionFromInactiveViewTest extends BaseTaskViewTest {

    def setup() {
        importAndWait(sampleProject())
        showTasksView()
        selectFooTask()
    }

    def "Double-clicking a task runs it"() {
        when:
        doubleClickSelectedTask()

        then:
        notThrown(Exception)
    }

    def "Double-clicking a task runs it while another part owns the workbench selection"() {
        setup:
        activateAnotherView()

        when:
        doubleClickSelectedTask()

        then:
        notThrown(Exception)
    }

    def "The context menu action runs the task while another part owns the workbench selection"() {
        setup:
        activateAnotherView()

        when:
        runContextMenuAction()

        then:
        notThrown(Exception)
    }

    private void runContextMenuAction() {
        Exception failure = null
        runOnUiThread {
            try {
                new RunTasksAction(UiPluginConstants.RUN_TASKS_COMMAND_ID, view).runWithEvent(new Event())
            } catch (Exception e) {
                failure = e
            }
        }
        if (failure != null) {
            throw failure
        }
    }

    private void doubleClickSelectedTask() {
        Exception failure = null
        runOnUiThread {
            try {
                def listener = new TreeViewerDoubleClickListener(UiPluginConstants.RUN_TASKS_COMMAND_ID, view.treeViewer)
                listener.doubleClick(new DoubleClickEvent(view.treeViewer, view.treeViewer.selection))
            } catch (Exception e) {
                failure = e
            }
        }
        if (failure != null) {
            throw failure
        }
    }

    private void showTasksView() {
        runOnUiThread { WorkbenchUtils.showView(TaskView.ID, null, IWorkbenchPage.VIEW_ACTIVATE) }
        tree.setFocus()
    }

    private void selectFooTask() {
        SWTBotTreeItem rootNode = tree.getTreeItem('root')
        rootNode.expand()
        SWTBotTreeItem groupNode = rootNode.getNode('custom')
        groupNode.expand()
        groupNode.items[0].select()
    }

    private void activateAnotherView() {
        for (String title : ['Project Explorer', 'Package Explorer', 'Outline']) {
            try {
                def otherView = bot.viewByTitle(title)
                otherView.show()
                otherView.setFocus()
                if (activePartId() != TaskView.ID) {
                    return
                }
            } catch (Exception e) {
                // the view is not part of the current perspective
            }
        }
        throw new IllegalStateException('no view available to take over the workbench selection')
    }

    private String activePartId() {
        String id = null
        runOnUiThread { id = PlatformUI.workbench.activeWorkbenchWindow.activePage.activePart?.site?.id }
        id
    }

    private File sampleProject() {
        dir('root') {
            file 'build.gradle', """
                task foo() {
                    group = 'custom'
                     doLast {
                        println "Running task on root project"
                     }
                 }
            """
        }
    }
}
