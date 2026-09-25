/*******************************************************************************
 * Copyright (c) 2023 Gradle Inc. and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.ui.internal.view;

import org.gradle.tooling.events.FailureResult;
import org.gradle.tooling.events.FinishEvent;
import org.gradle.tooling.events.OperationResult;

import com.google.common.base.Preconditions;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.AbstractTreeViewer;

import org.eclipse.buildship.ui.internal.PluginImages;
import org.eclipse.buildship.ui.internal.PluginImage.ImageState;
import org.eclipse.buildship.ui.internal.i18n.UiMessages;
import org.eclipse.buildship.ui.internal.util.nodeselection.NodeSelection;
import org.eclipse.buildship.ui.internal.util.nodeselection.SelectionSpecificAction;
import org.eclipse.buildship.ui.internal.view.execution.ExecutionPageContentProvider;
import org.eclipse.buildship.ui.internal.view.execution.OperationItem;

/**
 *
 */
public final class ExpandAllFailedTasksAction extends Action implements SelectionSpecificAction {

    private final AbstractTreeViewer treeViewer;
    private ExecutionPageContentProvider contentProvider = null;

    public ExpandAllFailedTasksAction(AbstractTreeViewer treeViewer) {
        super(null, AS_CHECK_BOX);
        this.treeViewer = Preconditions.checkNotNull(treeViewer);


        setToolTipText(UiMessages.Action_FilterFailedTasks_Tooltip);
        setImageDescriptor(PluginImages.OPERATION_FAILURE.withState(ImageState.ENABLED).getImageDescriptor());
    }

    public void setContentProvider(ExecutionPageContentProvider contentProvider) {
        this.contentProvider = contentProvider;
        setChecked(contentProvider.isFilterFailedItemsEnabled());
    }

    @Override
    public void run() {

        this.contentProvider.toggleFilterFailedItems();
        setChecked(this.contentProvider.isFilterFailedItemsEnabled());

        this.treeViewer.collapseAll();

        Object rootObject = this.treeViewer.getInput();

        if (rootObject instanceof OperationItem) {
            OperationItem root = (OperationItem) rootObject;
            recursivelyExpand(root);
        }

    }

    private void recursivelyExpand(OperationItem parent) {
        FinishEvent event = parent.getFinishEvent();
        if (event != null) {
            OperationResult result = event.getResult();
            if (result instanceof FailureResult) {
                // Result failed (expand ancestors up to this element)
                this.treeViewer.expandToLevel(parent, 0);
            }
        }
        for (OperationItem item: parent.getChildren()) {
            recursivelyExpand(item);
        }
    }

    @Override
    public boolean isVisibleFor(NodeSelection selection) {
        return true;
    }

    @Override
    public boolean isEnabledFor(NodeSelection selection) {
        return true;
    }

    @Override
    public void setEnabledFor(NodeSelection selection) {
        setEnabled(this.contentProvider != null);
    }

}
