/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Etienne Studer & Donát Csikós (Gradle Inc.) - initial API and implementation and initial documentation
 */

package org.eclipse.buildship.ui.view.execution;

import com.gradleware.tooling.toolingclient.BuildLaunchRequest;

import org.eclipse.buildship.core.launch.GradleRunConfigurationAttributes;
import org.eclipse.buildship.ui.view.MessagePage;
import org.eclipse.buildship.ui.view.MultiPageView;
import org.eclipse.buildship.ui.view.Page;
import org.eclipse.buildship.ui.view.SwitchToNextPageAction;

import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.PartInitException;

/**
 * A view displaying the Gradle executions.
 */
public final class ExecutionsView extends MultiPageView {

    // view id declared in the plugin.xml
    public static final String ID = "org.eclipse.buildship.ui.views.executionview"; //$NON-NLS-1$

    private ExecutionsViewState state;
    private IContributionItem switchPagesAction;

    @Override
    public void init(IViewSite site) throws PartInitException {
        super.init(site);

        // load the persisted state before we create any UI components that query for some state
        this.state = new ExecutionsViewState();
        this.state.load();

        // create the global actions
        this.switchPagesAction = new ActionContributionItem(new SwitchToNextPageAction(this, ExecutionsViewMessages.Action_SwitchExecutionPage_Tooltip));
        this.switchPagesAction.setVisible(false);

        // add actions to the global toolbar of the executions view
        IToolBarManager toolBarManager = site.getActionBars().getToolBarManager();
        toolBarManager.appendToGroup(PART_GROUP, this.switchPagesAction);

        // add actions to the global menu of the executions view
        IMenuManager menuManager = site.getActionBars().getMenuManager();
        menuManager.add(new ToggleShowTreeHeaderAction(this, this.state));
    }

    @Override
    protected void updateVisibilityOfGlobalActions() {
        super.updateVisibilityOfGlobalActions();
        this.switchPagesAction.setVisible(hasPages());
    }

    @Override
    protected Page createDefaultPage() {
        return new MessagePage(ExecutionsViewMessages.Label_No_Execution);
    }

    public void addExecutionPage(Job buildJob, String processName, BuildLaunchRequest buildLaunchRequest, GradleRunConfigurationAttributes attributes) {
        ExecutionPage executionPage = new ExecutionPage(buildJob, processName, buildLaunchRequest, this.state, attributes);
        addPage(executionPage);
        switchToPage(executionPage);
    }

    @Override
    public void dispose() {
        this.state.dispose();
        super.dispose();
    }

}
