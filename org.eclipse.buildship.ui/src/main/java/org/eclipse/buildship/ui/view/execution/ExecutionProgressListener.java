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

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import org.eclipse.buildship.ui.part.execution.model.OperationItem;
import org.eclipse.ui.PlatformUI;
import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.ProgressEvent;

import java.util.Map;

/**
 * Listens to {@link org.gradle.tooling.events.ProgressEvent} instances that are sent by the Tooling API while a build is executed. Each
 * incoming event is added to the execution tree as an {@link org.eclipse.buildship.ui.part.execution.model.OperationItem} instance.
 */
public final class ExecutionProgressListener implements org.gradle.tooling.events.ProgressListener {

    private final ExecutionPage executionPage;
    private final Map<OperationDescriptor, OperationItem> executionItemMap;
    private final DefaultOperationItemConfigurator operationItemConfigurator;

    public ExecutionProgressListener(ExecutionPage executionPage, OperationItem root) {
        this.executionPage = Preconditions.checkNotNull(executionPage);
        this.executionItemMap = Maps.newLinkedHashMap();
        this.executionItemMap.put(null, Preconditions.checkNotNull(root));
        this.operationItemConfigurator = new DefaultOperationItemConfigurator();
    }

    @Override
    public void statusChanged(ProgressEvent progressEvent) {
        // create or get the OperationItem for the descriptor of the given progress event
        OperationDescriptor descriptor = progressEvent.getDescriptor();
        OperationItem operationItem = this.executionItemMap.get(descriptor);
        boolean createdNewOperationItem = false;
        if (null == operationItem) {
            operationItem = new OperationItem(descriptor);
            this.executionItemMap.put(descriptor, operationItem);
            createdNewOperationItem = true;
        }

        // configure the operation item based on the event details
        this.operationItemConfigurator.configure(operationItem, progressEvent);

        // attach to parent, if this is a new operation (in case of StartEvent)
        OperationItem parentExecutionItem = this.executionItemMap.get(descriptor.getParent());
        parentExecutionItem.addChild(operationItem);

        // ensure the newly added node is made visible
        if (createdNewOperationItem) {
            makeNodeVisible(operationItem);
        }
    }

    private void makeNodeVisible(final OperationItem operationItem) {
        PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
            @Override
            public void run() {
                ExecutionProgressListener.this.executionPage.getFilteredTree().getViewer().expandToLevel(operationItem, 0);
            }
        });
    }

}
