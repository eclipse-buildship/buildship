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

import com.gradleware.tooling.toolingclient.BuildLaunchRequest;

import org.eclipse.core.databinding.beans.BeanProperties;
import org.eclipse.core.databinding.beans.IBeanValueProperty;
import org.eclipse.core.databinding.observable.set.IObservableSet;
import org.eclipse.core.databinding.property.list.IListProperty;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.databinding.viewers.ObservableListTreeContentProvider;
import org.eclipse.jface.databinding.viewers.ObservableMapCellLabelProvider;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IActionBars;

import org.eclipse.buildship.ui.PluginImage.ImageState;
import org.eclipse.buildship.ui.PluginImages;
import org.eclipse.buildship.ui.generic.CollapseTreeNodesAction;
import org.eclipse.buildship.ui.generic.ExpandTreeNodesAction;
import org.eclipse.buildship.ui.generic.GotoTestElementAction;
import org.eclipse.buildship.ui.generic.ShowConsolePageAction;
import org.eclipse.buildship.ui.view.BasePage;
import org.eclipse.buildship.ui.view.MultiPageView;
import org.eclipse.buildship.ui.view.PageSite;
import org.eclipse.buildship.ui.view.RemoveAllPagesAction;
import org.eclipse.buildship.ui.view.RemovePageAction;
import org.eclipse.buildship.ui.view.execution.listener.ExecutionPageContextMenuListener;
import org.eclipse.buildship.ui.viewer.FilteredTree;
import org.eclipse.buildship.ui.viewer.labelprovider.ObservableMapCellWithIconLabelProvider;

/**
 * Displays the tree of a single build execution.
 */
@SuppressWarnings("unchecked")
public final class ExecutionPage extends BasePage<FilteredTree> {

    private Job buildJob;
    private String displayName;
    private BuildLaunchRequest buildLaunchRequest;
    private ExecutionsViewState state;

    public ExecutionPage(Job buildJob, String displayName, BuildLaunchRequest buildLaunchRequest, ExecutionsViewState state) {
        this.buildJob = buildJob;
        this.displayName = displayName;
        this.buildLaunchRequest = buildLaunchRequest;
        this.state = state;
    }

    public Job getBuildJob() {
        return this.buildJob;
    }

    @Override
    public String getDisplayName() {
        return this.displayName;
    }

    @Override
    public FilteredTree createPageWithResult(Composite parent) {
        // configure tree
        FilteredTree filteredTree = new FilteredTree(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL, new OperationItemPatternFilter());
        filteredTree.setShowFilterControls(false);
        filteredTree.getViewer().getTree().setHeaderVisible(this.state.isShowTreeHeader());

        TreeViewerColumn nameColumn = new TreeViewerColumn(filteredTree.getViewer(), SWT.NONE);
        nameColumn.getColumn().setText(ExecutionsViewMessages.Tree_Column_Operation_Name_Text);
        nameColumn.getColumn().setWidth(550);

        TreeViewerColumn durationColumn = new TreeViewerColumn(filteredTree.getViewer(), SWT.NONE);
        durationColumn.getColumn().setText(ExecutionsViewMessages.Tree_Column_Operation_Duration_Text);
        durationColumn.getColumn().setWidth(200);

        // configure data binding
        IListProperty childrenProperty = new OperationItemChildrenListProperty();
        ObservableListTreeContentProvider contentProvider = new ObservableListTreeContentProvider(childrenProperty.listFactory(), null);
        filteredTree.getViewer().setContentProvider(contentProvider);

        IObservableSet knownElements = contentProvider.getKnownElements();
        attachLabelProvider(OperationItem.FIELD_NAME, OperationItem.FIELD_IMAGE, knownElements, nameColumn);
        attachLabelProvider(OperationItem.FIELD_DURATION, null, knownElements, durationColumn);

        // set tree root node
        OperationItem root = new OperationItem();
        filteredTree.getViewer().setInput(root);

        // listen to progress events
        this.buildLaunchRequest.typedProgressListeners(new ExecutionProgressListener(this, root));

        // return the tree as the outermost page control
        return filteredTree;
    }

    private void attachLabelProvider(String textProperty, String imageProperty, IObservableSet knownElements, ViewerColumn viewerColumn) {
        IBeanValueProperty txtProperty = BeanProperties.value(textProperty);
        if (imageProperty != null) {
            IBeanValueProperty imgProperty = BeanProperties.value(imageProperty);
            viewerColumn.setLabelProvider(new ObservableMapCellWithIconLabelProvider(txtProperty.observeDetail(knownElements), imgProperty.observeDetail(knownElements)));
        } else {
            viewerColumn.setLabelProvider(new ObservableMapCellLabelProvider(txtProperty.observeDetail(knownElements)));
        }
    }

    @Override
    public void init(PageSite pageSite) {
        super.init(pageSite);

        IActionBars actionBars = getSite().getActionBars();
        IToolBarManager toolbarManager = actionBars.getToolBarManager();
        MultiPageView view = (MultiPageView) getSite().getViewSite().getPart();
        toolbarManager.appendToGroup(MultiPageView.PAGE_GROUP, new ExpandTreeNodesAction(getPageControl().getViewer()));
        toolbarManager.appendToGroup(MultiPageView.PAGE_GROUP, new CollapseTreeNodesAction(getPageControl().getViewer()));
        toolbarManager.appendToGroup(MultiPageView.PAGE_GROUP, new ShowFilterAction(this));
        toolbarManager.appendToGroup(MultiPageView.PAGE_GROUP, new Separator());
        toolbarManager.appendToGroup(MultiPageView.PAGE_GROUP, new CancelBuildExecutionAction(this));
        toolbarManager.appendToGroup(MultiPageView.PAGE_GROUP, new RemovePageAction(this, ExecutionsViewMessages.Action_RemoveExecutionPage_Text));
        toolbarManager.appendToGroup(MultiPageView.PAGE_GROUP, new RemoveAllPagesAction(view, ExecutionsViewMessages.Action_RemoveAllExecutionPages_Text));
        toolbarManager.appendToGroup(MultiPageView.PAGE_GROUP, new ShowConsolePageAction("Show in console", "Shows the console of this build in the console view",
                PluginImages.CONSOLE_ICON.withState(ImageState.ENABLED).getImageDescriptor(), getDisplayName()));
        toolbarManager.update(true);

        // add a context menu to the Tree
        registerContextMenu(pageSite);

        registerDoubleClickAction();

    }

    private void registerDoubleClickAction() {
        // navigate to source file on double click or when pressing enter
        getPageControl().getViewer().addDoubleClickListener(new IDoubleClickListener() {

            @Override
            public void doubleClick(DoubleClickEvent event) {
                Viewer viewer = event.getViewer();
                GotoTestElementAction gotoTestElementAction = new GotoTestElementAction(viewer, viewer.getControl().getDisplay());
                gotoTestElementAction.run();
            }
        });
    }

    private void registerContextMenu(PageSite pageSite) {
        final MenuManager menuManager = new MenuManager();
        menuManager.setRemoveAllWhenShown(true);
        menuManager.addMenuListener(new ExecutionPageContextMenuListener(getPageControl().getViewer()));

        Menu contextMenu = menuManager.createContextMenu(getPageControl().getViewer().getControl());
        getPageControl().getViewer().getTree().setMenu(contextMenu);

        pageSite.getViewSite().registerContextMenu(menuManager, getPageControl().getViewer());
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Object getAdapter(Class adapter) {
        if (FilteredTree.class.equals(adapter)) {
            return getPageControl();
        } else if (adapter.isAssignableFrom(TreeViewer.class)) {
            // isAssignableFrom also applies for the ISelectionProvider interface
            return getPageControl().getViewer();
        }
        return Platform.getAdapterManager().getAdapter(this, adapter);
    }

}
