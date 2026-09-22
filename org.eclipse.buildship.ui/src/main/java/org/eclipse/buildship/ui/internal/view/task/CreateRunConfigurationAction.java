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

import com.google.common.base.Optional;

import org.eclipse.buildship.core.internal.CorePlugin;
import org.eclipse.buildship.core.internal.launch.GradleRunConfigurationAttributes;
import org.eclipse.buildship.ui.internal.util.action.CommandBackedAction;
import org.eclipse.buildship.ui.internal.util.nodeselection.NodeSelection;
import org.eclipse.buildship.ui.internal.util.nodeselection.NodeSelectionProvider;
import org.eclipse.buildship.ui.internal.util.nodeselection.SelectionSpecificAction;

/**
 * Creates a new run configuration for the selected Gradle tasks.
 * <p/>
 * Note that {@link CreateRunConfigurationAction} and {@link OpenRunConfigurationAction} are mutually exclusive.
 */
public final class CreateRunConfigurationAction extends CommandBackedAction implements SelectionSpecificAction {

    public CreateRunConfigurationAction(String commandId, NodeSelectionProvider selectionProvider) {
        super(commandId, selectionProvider);

        setText(TaskViewMessages.Action_CreateRunConfiguration_Text);
        setToolTipText(TaskViewMessages.Action_CreateRunConfiguration_Tooltip);
    }

    @Override
    public boolean isVisibleFor(NodeSelection selection) {
        return (TaskViewActionStateRules.taskScopedTaskExecutionActionsVisibleFor(selection) ||
                TaskViewActionStateRules.projectScopedTaskExecutionActionsVisibleFor(selection)) && isValidSelection(selection);
    }

    @Override
    public boolean isEnabledFor(NodeSelection selection) {
        return (TaskViewActionStateRules.taskScopedTaskExecutionActionsEnablement(selection).asBoolean() ||
                TaskViewActionStateRules.projectScopedTaskExecutionActionsEnabledFor(selection)) && isValidSelection(selection);
    }

    private boolean isValidSelection(NodeSelection selection) {
        Optional<GradleRunConfigurationAttributes> attributes = TaskNodeSelectionUtils.tryGetRunConfigurationAttributes(selection);
        return !attributes.isPresent() || !CorePlugin.gradleLaunchConfigurationManager().getRunConfiguration(attributes.get()).isPresent();
    }

    @Override
    public void setEnabledFor(NodeSelection selection) {
        setEnabled(isEnabledFor(selection));
    }

}
