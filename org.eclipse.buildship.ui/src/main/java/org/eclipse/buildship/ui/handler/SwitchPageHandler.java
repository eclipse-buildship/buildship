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

import java.util.Map;

import com.google.common.primitives.Ints;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.expressions.IEvaluationContext;
import org.eclipse.ui.ISources;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.IElementUpdater;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.menus.UIElement;

import org.eclipse.buildship.ui.part.AbstractPagePart;

/**
 * Switches either to the next page of an {@link AbstractPagePart} or to the page at
 * the index defined in the {@link SwitchPageHandler#PAGE_INDEX_PARAM} command parameter.
 */
public final class SwitchPageHandler extends AbstractHandler implements IElementUpdater {

    public static final String SWITCH_PAGE_COMMAND_ID = "org.eclipse.buildship.ui.commands.switchpage"; //$NON-NLS-1$
    public static final String PAGE_INDEX_PARAM = "org.eclipse.buildship.ui.commandParameters.pageindex"; //$NON-NLS-1$

    @SuppressWarnings("rawtypes")
    @Override
    public void updateElement(UIElement element, Map parameters) {
        IWorkbenchPart activePart = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActivePart();
        if (activePart instanceof AbstractPagePart) {
            AbstractPagePart pagePart = (AbstractPagePart) activePart;
            String pageIndexParameter = (String) parameters.get(PAGE_INDEX_PARAM);
            if (pageIndexParameter != null) {
                // compare the given page index parameter with the index of the current page
                int commandParamIndex = Ints.tryParse(pageIndexParameter);
                int currentPageIndex = pagePart.getPages().indexOf(pagePart.getCurrentPage());
                element.setChecked(currentPageIndex == commandParamIndex);
            }
        }
    }

    @Override
    public void setEnabled(Object evaluationContext) {
        boolean enabled = false;
        if (evaluationContext instanceof IEvaluationContext) {
            Object activePart = ((IEvaluationContext) evaluationContext).getVariable(ISources.ACTIVE_PART_NAME);
            if (activePart instanceof AbstractPagePart) {
                enabled = ((AbstractPagePart) activePart).hasPages();
            }
        }
        setBaseEnabled(enabled);
    }

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IWorkbenchPart activePart = HandlerUtil.getActivePart(event);
        if (activePart instanceof AbstractPagePart) {
            AbstractPagePart pagePart = (AbstractPagePart) activePart;
            String pageIndexParameter = event.getParameter(PAGE_INDEX_PARAM);
            if (pageIndexParameter != null) {
                // switch to the page set in the given page parameter
                int index = Integer.parseInt(pageIndexParameter);
                pagePart.switchToPageAtIndex(index);
                return null;
            } else {
                // switch to the next page
                pagePart.switchToNextPage();
                return null;
            }
        }
        return null;
    }


}
