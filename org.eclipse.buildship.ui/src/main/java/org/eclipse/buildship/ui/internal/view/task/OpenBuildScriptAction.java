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

import org.eclipse.buildship.ui.internal.util.action.CommandBackedAction;
import org.eclipse.buildship.ui.internal.util.nodeselection.NodeSelection;
import org.eclipse.buildship.ui.internal.util.nodeselection.NodeSelectionProvider;
import org.eclipse.buildship.ui.internal.util.nodeselection.SelectionSpecificAction;

/**
 * Opens the build script of the selected Gradle project.
 */
public final class OpenBuildScriptAction extends CommandBackedAction implements SelectionSpecificAction {

    public OpenBuildScriptAction(String commandId, NodeSelectionProvider selectionProvider) {
        super(commandId, selectionProvider);

        setText(TaskViewMessages.Action_OpenBuildScript_Text);
        setToolTipText(TaskViewMessages.Action_OpenBuildScript_Tooltip);
    }

    @Override
    public boolean isVisibleFor(NodeSelection selection) {
        return isEnabledFor(selection);
    }

    @Override
    public boolean isEnabledFor(NodeSelection selection) {
        return isEnabledForSelection(selection);
    }

    @Override
    public void setEnabledFor(NodeSelection selection) {
        setEnabled(isEnabledFor(selection));
    }

    public static boolean isEnabledForSelection(NodeSelection selection) {
        return !selection.isEmpty() && selection.hasAllNodesOfType(ProjectNode.class);
    }

}
