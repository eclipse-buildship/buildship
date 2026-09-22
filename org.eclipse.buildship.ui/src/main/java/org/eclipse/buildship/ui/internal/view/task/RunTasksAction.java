/*******************************************************************************
 * Copyright (c) 2023 Gradle Inc. and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.ui.internal.view.task;

import org.eclipse.buildship.ui.internal.PluginImage;
import org.eclipse.buildship.ui.internal.PluginImages;
import org.eclipse.buildship.ui.internal.util.action.CommandBackedAction;
import org.eclipse.buildship.ui.internal.util.nodeselection.NodeSelection;
import org.eclipse.buildship.ui.internal.util.nodeselection.NodeSelectionProvider;
import org.eclipse.buildship.ui.internal.util.nodeselection.SelectionSpecificAction;
import org.eclipse.buildship.ui.internal.view.task.TaskViewActionStateRules.TaskScopedActionEnablement;

/**
 * Runs the selected Gradle tasks.
 */
public final class RunTasksAction extends CommandBackedAction implements SelectionSpecificAction {

    @SuppressWarnings("cast")
    public RunTasksAction(String commandId, NodeSelectionProvider selectionProvider) {
        super(commandId, selectionProvider);

        setText(TaskViewMessages.Action_RunTasks_Text);
        setToolTipText(TaskViewMessages.Action_RunTasks_Tooltip);
        setImageDescriptor(PluginImages.RUN_TASKS.withState(PluginImage.ImageState.ENABLED).getImageDescriptor());
    }

    @Override
    public boolean isVisibleFor(NodeSelection selection) {
        return TaskViewActionStateRules.taskScopedTaskExecutionActionsVisibleFor(selection);
    }

    @Override
    public boolean isEnabledFor(NodeSelection selection) {
        return TaskViewActionStateRules.taskScopedTaskExecutionActionsEnablement(selection).asBoolean();
    }

    @Override
    public void setEnabledFor(NodeSelection selection) {
        TaskScopedActionEnablement enablement = TaskViewActionStateRules.taskScopedTaskExecutionActionsEnablement(selection);

        boolean isEnabled = enablement.asBoolean();
        setEnabled(isEnabled);
        setImageDescriptor(PluginImages.RUN_TASKS.withState(isEnabled ? PluginImages.ImageState.ENABLED : PluginImage.ImageState.DISABLED).getImageDescriptor());

        switch (enablement) {
            case ENABLED:
                setText(TaskViewMessages.Action_RunTasks_Text);
                break;
            case DISABLED_MULTIPLE_ROOT_PROJECTS:
                setText(TaskViewMessages.Action_RunTasks_Text_Multiple_Root);
                break;
            case DISABLED_NO_ROOT_PROJECT:
                setText(TaskViewMessages.Action_RunTasks_Text_Disabled_NonStandard_layout);
                break;
            case COMPOSITE_WITH_UNSUPPORTED_GRADLE_VERSION:
                setText(TaskViewMessages.Action_RunTasks_Text_No_Support_For_Task_Execution_In_Included_build);
                break;
            default:
                setText(TaskViewMessages.Action_RunTasks_Text_Disabled_Other);
                break;
        }
    }
}
