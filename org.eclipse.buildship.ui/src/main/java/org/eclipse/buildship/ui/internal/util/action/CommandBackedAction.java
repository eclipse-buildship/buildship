/*******************************************************************************
 * Copyright (c) 2023 Gradle Inc. and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.ui.internal.util.action;

import com.google.common.base.Preconditions;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.swt.widgets.Event;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.IHandlerService;

import org.eclipse.buildship.core.internal.GradlePluginsRuntimeException;
import org.eclipse.buildship.ui.internal.UiPlugin;
import org.eclipse.buildship.ui.internal.util.nodeselection.NodeSelectionProvider;

/**
 * Base class for {@link Action} instances that invoke a {@link org.eclipse.core.commands.Command}
 * instance via its command id when the action is triggered.
 */
public abstract class CommandBackedAction extends Action {

    private final String commandId;
    private final NodeSelectionProvider selectionProvider;

    protected CommandBackedAction(String commandId) {
        this(commandId, IAction.AS_UNSPECIFIED, null);
    }

    protected CommandBackedAction(String commandId, NodeSelectionProvider selectionProvider) {
        this(commandId, IAction.AS_UNSPECIFIED, selectionProvider);
    }

    protected CommandBackedAction(String commandId, int style) {
        this(commandId, style, null);
    }

    private CommandBackedAction(String commandId, int style, NodeSelectionProvider selectionProvider) {
        super(null, style);
        this.commandId = Preconditions.checkNotNull(commandId);
        this.selectionProvider = selectionProvider;
    }

    @Override
    public void runWithEvent(Event event) {
        try {
            if (this.selectionProvider == null) {
                getHandlerService().executeCommand(this.commandId, event);
            } else {
                CommandUtils.executeCommandForSelection(this.commandId, event, this.selectionProvider.getSelection());
            }
        } catch (Exception e) {
            String message = String.format("Cannot execute command for action '%s'.", getText());
            UiPlugin.logger().error(message, e);
            throw new GradlePluginsRuntimeException(message, e);
        }
    }

    @SuppressWarnings({"cast", "RedundantCast"})
    private IHandlerService getHandlerService() {
        return (IHandlerService) PlatformUI.getWorkbench().getActiveWorkbenchWindow().getService(IHandlerService.class);
    }

}
