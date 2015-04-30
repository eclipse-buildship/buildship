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

package org.eclipse.buildship.ui.part.execution;

import java.util.List;

import com.google.common.collect.Lists;

import org.eclipse.core.databinding.beans.BeanProperties;
import org.eclipse.core.databinding.beans.IBeanValueProperty;
import org.eclipse.core.databinding.observable.set.IObservableSet;
import org.eclipse.core.databinding.property.list.IListProperty;
import org.eclipse.jface.databinding.viewers.ObservableListTreeContentProvider;
import org.eclipse.jface.databinding.viewers.ObservableMapCellLabelProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jface.viewers.ViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

import org.eclipse.buildship.ui.PluginImage.ImageState;
import org.eclipse.buildship.ui.PluginImages;
import org.eclipse.buildship.ui.part.FilteredTreeProvider;
import org.eclipse.buildship.ui.part.execution.model.OperationItem;
import org.eclipse.buildship.ui.part.execution.model.internal.ExecutionChildrenListProperty;
import org.eclipse.buildship.ui.part.pages.IPage;
import org.eclipse.buildship.ui.viewer.FilteredTree;
import org.eclipse.buildship.ui.viewer.PatternFilter;
import org.eclipse.buildship.ui.viewer.labelprovider.ObservableMapCellWithIconLabelProvider;

/**
 * This part displays the Gradle executions, like a build. It contains a FilteredTree with an
 * operation and a duration column.
 *
 */
public class ExecutionPage implements IPage, FilteredTreeProvider {

    private FilteredTree filteredTree;

    private TreeViewerColumn labelColumn;

    private TreeViewerColumn durationColumn;

    private OperationItem root = new OperationItem(null);

    private OperationItem buildStarted;

    private String displayName;

    @Override
    public void createPage(Composite parent) {

        ExecutionPartPreferences partPrefs = new ExecutionPartPreferences();

        filteredTree = new FilteredTree(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER, new PatternFilter());
        filteredTree.setShowFilterControls(partPrefs.getFilterVisibile());
        filteredTree.getViewer().getTree().setHeaderVisible(partPrefs.getHeaderVisibile());

        createViewerColumns();

        bindUI();
    }

    @Override
    public void setFocus() {
        if (getViewer() != null && getViewer().getControl() != null && !getViewer().getControl().isDisposed()) {
            getViewer().getControl().setFocus();
        }
    }

    @Override
    public FilteredTree getFilteredTree() {
        return filteredTree;
    }

    @Override
    public TreeViewer getViewer() {
        if (getFilteredTree() != null && !getFilteredTree().isDisposed()) {
            return getFilteredTree().getViewer();
        }
        return null;
    }

    @Override
    public void dispose() {
        if (getPageControl() != null && !getPageControl().isDisposed()) {
            getPageControl().dispose();
            filteredTree = null;
        }
    }

    protected void createViewerColumns() {
        labelColumn = new TreeViewerColumn(getViewer(), SWT.NONE);
        labelColumn.getColumn().setText("Operations");
        labelColumn.getColumn().setWidth(450);

        durationColumn = new TreeViewerColumn(getViewer(), SWT.NONE);
        durationColumn.getColumn().setText("Duration");
        durationColumn.getColumn().setWidth(200);
    }

    private void bindUI() {
        IListProperty childrenProperty = new ExecutionChildrenListProperty();

        ObservableListTreeContentProvider contentProvider = new ObservableListTreeContentProvider(childrenProperty.listFactory(), null);
        getViewer().setContentProvider(contentProvider);

        IObservableSet knownElements = contentProvider.getKnownElements();
        attachLabelProvider(OperationItem.FIELD_LABEL, OperationItem.FIELD_IMAGE, knownElements, labelColumn);
        attachLabelProvider(OperationItem.FIELD_DURATION, null, knownElements, durationColumn);

        getViewer().setInput(root);

        List<OperationItem> rootChildren = Lists.newArrayList();
        buildStarted = new OperationItem(null, "Gradle Build");
        buildStarted.setImage(PluginImages.GRADLE_ICON.withState(ImageState.ENABLED).getImageDescriptor());
        rootChildren.add(buildStarted);
        root.setChildren(rootChildren);
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
    public Control getPageControl() {
        return getFilteredTree();
    }

    public OperationItem getBuildStartedItem() {
        return buildStarted;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

}
