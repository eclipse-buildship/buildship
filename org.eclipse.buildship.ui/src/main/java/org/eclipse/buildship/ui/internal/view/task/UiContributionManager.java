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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import org.eclipse.jface.action.GroupMarker;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.widgets.Menu;

import org.eclipse.buildship.core.internal.CorePlugin;
import org.eclipse.buildship.ui.internal.UiPluginConstants;
import org.eclipse.buildship.ui.internal.util.nodeselection.ActionEnablingSelectionChangedListener;
import org.eclipse.buildship.ui.internal.util.nodeselection.ActionShowingContextMenuListener;
import org.eclipse.buildship.ui.internal.util.nodeselection.SelectionSpecificAction;
import org.eclipse.buildship.ui.internal.util.selection.ContextActivatingViewPartListener;
import org.eclipse.buildship.ui.internal.view.CollapseAllTreeNodesAction;
import org.eclipse.buildship.ui.internal.view.ExpandAllFailedTasksAction;
import org.eclipse.buildship.ui.internal.view.ExpandAllTreeNodesAction;
import org.eclipse.buildship.ui.internal.view.ShowFilterAction;

/**
 * Adds UI contributions to the {@link TaskView}.
 */
public final class UiContributionManager {

    private static final String TOOLBAR_MISC_GROUP = "toolbarMiscGroup";
    private static final String TOOLBAR_TREE_GROUP = "toolbarTreeGroup";
    private static final String MENU_SORTING_GROUP = "toolbarSortingGroup";
    private static final String MENU_FILTERING_GROUP = "menuFilteringGroup";
    private static final String MENU_GROUPING_GROUP = "menuGroupingGroup";

    private final TaskView taskView;
    private final ImmutableList<SelectionSpecificAction> toolBarActions;
    private final ImmutableList<SelectionSpecificAction> contextMenuActions;
    private final ImmutableList<SelectionSpecificAction> contextMenuActionsPrecededBySeparator;
    private final ImmutableList<SelectionSpecificAction> contextMenuActionsSucceededBySeparator;
    private final ActionEnablingSelectionChangedListener toolBarActionsSelectionChangedListener;
    private final TreeViewerSelectionChangeListener treeViewerSelectionChangeListener;
    private final TreeViewerDoubleClickListener treeViewerDoubleClickListener;
    private final ContextActivatingViewPartListener contextActivatingViewPartListener;
    private final WorkbenchSelectionListener workbenchSelectionListener;
    private final WorkspaceProjectsChangeListener workspaceProjectsChangeListener;

    public UiContributionManager(TaskView taskView) {
        this.taskView = Preconditions.checkNotNull(taskView);

        // create actions
        RunTasksAction runTasksAction = new RunTasksAction(UiPluginConstants.RUN_TASKS_COMMAND_ID);
        RunDefaultTasksAction runDefaultTasksAction = new RunDefaultTasksAction(UiPluginConstants.RUN_DEFAULT_TASKS_COMMAND_ID);
        CreateRunConfigurationAction createRunConfigurationAction = new CreateRunConfigurationAction(UiPluginConstants.OPEN_RUN_CONFIGURATION_COMMAND_ID);
        OpenRunConfigurationAction openRunConfigurationAction = new OpenRunConfigurationAction(UiPluginConstants.OPEN_RUN_CONFIGURATION_COMMAND_ID);
        OpenBuildScriptAction openBuildScriptAction = new OpenBuildScriptAction(UiPluginConstants.OPEN_BUILD_SCRIPT_COMMAND_ID);

        // add selection-sensitive tool bar actions
        this.toolBarActions = ImmutableList.of();

        // add selection-sensitive context menu actions
        this.contextMenuActions = ImmutableList.<SelectionSpecificAction>of(runTasksAction, runDefaultTasksAction,
                createRunConfigurationAction, openRunConfigurationAction, openBuildScriptAction);
        this.contextMenuActionsPrecededBySeparator = ImmutableList.<SelectionSpecificAction>of(openBuildScriptAction);
        this.contextMenuActionsSucceededBySeparator = ImmutableList.of();

        // create listeners
        this.toolBarActionsSelectionChangedListener = new ActionEnablingSelectionChangedListener(taskView, this.toolBarActions);
        this.treeViewerSelectionChangeListener = new TreeViewerSelectionChangeListener(taskView);
        this.treeViewerDoubleClickListener = new TreeViewerDoubleClickListener(UiPluginConstants.RUN_TASKS_COMMAND_ID, taskView.getTreeViewer());
        this.contextActivatingViewPartListener = new ContextActivatingViewPartListener(UiPluginConstants.TASKVIEW_CONTEXT_ID, taskView);
        this.workbenchSelectionListener = new WorkbenchSelectionListener(taskView);
        this.workspaceProjectsChangeListener = new WorkspaceProjectsChangeListener(taskView);
    }

    /**
     * Wires all UI contributions into the task view.
     */
    public void wire() {
        populateToolBar();
        populateMenu();
        registerContextMenu();
        registerListeners();
    }

    private void populateToolBar() {
        IToolBarManager manager = this.taskView.getViewSite().getActionBars().getToolBarManager();
        manager.add(new GroupMarker(TOOLBAR_TREE_GROUP));
        manager.appendToGroup(TOOLBAR_TREE_GROUP, new ExpandAllFailedTasksAction(this.taskView.getTreeViewer()));
        manager.appendToGroup(TOOLBAR_TREE_GROUP, new ExpandAllTreeNodesAction(this.taskView.getTreeViewer()));
        manager.appendToGroup(TOOLBAR_TREE_GROUP, new CollapseAllTreeNodesAction(this.taskView.getTreeViewer()));
        manager.appendToGroup(TOOLBAR_TREE_GROUP, new ShowFilterAction(this.taskView.getFilteredTree()));
        manager.appendToGroup(TOOLBAR_TREE_GROUP, new Separator());
        manager.add(new GroupMarker(TOOLBAR_MISC_GROUP));
        manager.appendToGroup(TOOLBAR_MISC_GROUP, new RefreshViewAction(UiPluginConstants.REFRESH_TASKVIEW_COMMAND_ID));
        manager.appendToGroup(TOOLBAR_MISC_GROUP, new ToggleLinkToSelectionAction(this.taskView));
    }

    private void populateMenu() {
        IMenuManager manager = this.taskView.getViewSite().getActionBars().getMenuManager();
        manager.add(new Separator(MENU_FILTERING_GROUP));
        manager.appendToGroup(MENU_FILTERING_GROUP, new FilterTaskSelectorsAction(this.taskView));
        manager.appendToGroup(MENU_FILTERING_GROUP, new FilterProjectTasksAction(this.taskView));
        manager.appendToGroup(MENU_FILTERING_GROUP, new FilterPrivateTasksAction(this.taskView));
        manager.add(new Separator(MENU_SORTING_GROUP));
        manager.appendToGroup(MENU_SORTING_GROUP, new SortTasksByTypeAction(this.taskView));
        manager.appendToGroup(MENU_SORTING_GROUP, new SortTasksByVisibilityAction(this.taskView));
        manager.add(new Separator(MENU_GROUPING_GROUP));
        manager.appendToGroup(MENU_GROUPING_GROUP, new GroupTasksAction(this.taskView));
        manager.appendToGroup(MENU_GROUPING_GROUP, new ShowFlattenedProjectHierarchy(this.taskView));
    }

    private void registerContextMenu() {
        TreeViewer treeViewer = this.taskView.getTreeViewer();
        MenuManager menuManager = new MenuManager();
        menuManager.setRemoveAllWhenShown(true);
        menuManager.addMenuListener(new ActionShowingContextMenuListener(this.taskView, this.contextMenuActions, this.contextMenuActionsPrecededBySeparator, this.contextMenuActionsSucceededBySeparator));
        Menu contextMenu = menuManager.createContextMenu(treeViewer.getTree());
        treeViewer.getTree().setMenu(contextMenu);
        this.taskView.getViewSite().registerContextMenu(menuManager, treeViewer);
    }

    private void registerListeners() {
        this.taskView.getTreeViewer().addSelectionChangedListener(this.toolBarActionsSelectionChangedListener);
        this.taskView.getTreeViewer().addSelectionChangedListener(this.treeViewerSelectionChangeListener);
        this.taskView.getTreeViewer().addDoubleClickListener(this.treeViewerDoubleClickListener);
        this.taskView.getSite().getPage().addPartListener(this.contextActivatingViewPartListener);
        this.taskView.getSite().getWorkbenchWindow().getSelectionService().addSelectionListener(this.workbenchSelectionListener);
        CorePlugin.listenerRegistry().addEventListener(this.workspaceProjectsChangeListener);
    }

    public void dispose() {
        this.taskView.getTreeViewer().removeSelectionChangedListener(this.toolBarActionsSelectionChangedListener);
        this.taskView.getTreeViewer().removeSelectionChangedListener(this.treeViewerSelectionChangeListener);
        this.taskView.getTreeViewer().removeDoubleClickListener(this.treeViewerDoubleClickListener);
        this.taskView.getSite().getPage().removePartListener(this.contextActivatingViewPartListener);
        this.taskView.getSite().getWorkbenchWindow().getSelectionService().removeSelectionListener(this.workbenchSelectionListener);
        CorePlugin.listenerRegistry().removeEventListener(this.workspaceProjectsChangeListener);
    }

}
