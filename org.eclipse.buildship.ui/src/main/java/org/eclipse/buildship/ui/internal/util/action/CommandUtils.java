/*******************************************************************************
 * Copyright (c) 2026 Gradle Inc. and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.ui.internal.util.action;

import org.eclipse.core.commands.Command;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.NotEnabledException;
import org.eclipse.core.commands.NotHandledException;
import org.eclipse.core.commands.ParameterizedCommand;
import org.eclipse.core.commands.common.NotDefinedException;
import org.eclipse.core.expressions.IEvaluationContext;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.widgets.Event;
import org.eclipse.ui.ISources;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.handlers.IHandlerService;

/**
 * Executes commands against an explicitly supplied selection instead of against the selection of
 * the currently active workbench part.
 */
public final class CommandUtils {

    private CommandUtils() {
    }

    @SuppressWarnings({"cast", "RedundantCast"})
    public static Object executeCommandForSelection(String commandId, Event event, ISelection selection)
            throws ExecutionException, NotDefinedException, NotEnabledException, NotHandledException {
        IHandlerService handlerService = (IHandlerService) PlatformUI.getWorkbench().getService(IHandlerService.class);
        ICommandService commandService = (ICommandService) PlatformUI.getWorkbench().getService(ICommandService.class);

        Command command = commandService.getCommand(commandId);
        IEvaluationContext evaluationContext = handlerService.createContextSnapshot(false);
        evaluationContext.addVariable(ISources.ACTIVE_CURRENT_SELECTION_NAME, selection);

        return handlerService.executeCommandInContext(ParameterizedCommand.generateCommand(command, null), event, evaluationContext);
    }

}
