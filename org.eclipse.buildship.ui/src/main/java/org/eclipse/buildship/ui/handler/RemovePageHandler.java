/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Simon Scholz (vogella GmbH) - initial API and implementation and initial documentation
 *     Etienne Studer & Donát Csikós (Gradle Inc.) - refactoring and integration
 */

package org.eclipse.buildship.ui.handler;

import org.eclipse.buildship.ui.part.AbstractPagePart;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.expressions.IEvaluationContext;
import org.eclipse.ui.ISources;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * Removes the currently selected page from an {@link AbstractPagePart}.
 */
public final class RemovePageHandler extends AbstractHandler {

    @Override
    public void setEnabled(Object evaluationContext) {
        boolean enabled = false;
        if (evaluationContext instanceof IEvaluationContext) {
            Object activePart = ((IEvaluationContext) evaluationContext).getVariable(ISources.ACTIVE_PART_NAME);
            if (activePart instanceof AbstractPagePart) {
                enabled = ((AbstractPagePart) activePart).getCurrentPage() != null;
            }
        }
        setBaseEnabled(enabled);
    }

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IWorkbenchPart activePart = HandlerUtil.getActivePart(event);
        if (activePart instanceof AbstractPagePart) {
            AbstractPagePart pagePart = (AbstractPagePart) activePart;
            pagePart.removeCurrentPage();
        }
        return null;
    }

}
