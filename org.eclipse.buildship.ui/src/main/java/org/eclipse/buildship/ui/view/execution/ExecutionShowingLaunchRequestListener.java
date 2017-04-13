/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Simon Scholz (vogella GmbH) - initial API and implementation and initial documentation
 */

package org.eclipse.buildship.ui.view.execution;

import org.eclipse.buildship.core.console.ProcessDescription;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;

import org.eclipse.buildship.core.event.Event;
import org.eclipse.buildship.core.event.EventListener;
import org.eclipse.buildship.core.launch.ExecuteLaunchRequestEvent;
import org.eclipse.buildship.ui.util.workbench.WorkbenchUtils;

/**
 * {@link EventListener} implementation showing/activating the Executions View when a new Gradle
 * build is executed and the
 * {@link org.eclipse.buildship.core.launch.GradleRunConfigurationAttributes#isShowExecutionView()}
 * setting is enabled.
 * <p/>
 * The listener implementation is necessary since opening a view is a UI-related task and the
 * execution is performed in the core component.
 */
public final class ExecutionShowingLaunchRequestListener implements EventListener {

    @Override
    public void onEvent(Event event) {
        if (event instanceof ExecuteLaunchRequestEvent) {
            handleLaunchRequest((ExecuteLaunchRequestEvent) event);
        }
    }

    private void handleLaunchRequest(final ExecuteLaunchRequestEvent event) {
        // call synchronously to make sure we do not miss any progress events
        PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable() {

            @Override
            public void run() {
                ProcessDescription processDescription = event.getProcessDescription();

                // activate the executions view
                int mode = processDescription.getConfigurationAttributes().isShowExecutionView() ? IWorkbenchPage.VIEW_ACTIVATE : IWorkbenchPage.VIEW_CREATE;
                ExecutionsView view = WorkbenchUtils.showView(ExecutionsView.ID, null, mode);

                // show the launched build in a new page of the Executions View
                view.addExecutionPage(processDescription, event.getOperation());
            }
        });
    }

}
