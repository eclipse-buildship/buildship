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

package org.eclipse.buildship.ui.part.execution.listener;

import com.google.common.eventbus.Subscribe;

import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;

import org.eclipse.buildship.ui.part.ViewerProvider;
import org.eclipse.buildship.ui.part.execution.model.internal.OperationItemCreatedEvent;

/**
 * This class listens to {@link OperationItemCreatedEvent} events and expands the TreeViewer, so
 * that every new tree element is directly visible.
 */
public class ProgressItemCreatedListener {

    private ViewerProvider viewerPart;

    public ProgressItemCreatedListener(ViewerProvider treePart) {
        this.viewerPart = treePart;
    }

    @Subscribe
    public void progressItemCreated(OperationItemCreatedEvent progressItemCreatedEvent) {
        Viewer viewer = viewerPart.getViewer();
        if (viewer != null) {
            viewer.getControl().getDisplay().asyncExec(new Runnable() {

                @Override
                public void run() {
                    Viewer viewer = viewerPart.getViewer();
                    if (viewer instanceof TreeViewer) {
                        ((TreeViewer) viewer).expandAll();
                    }
                }
            });
        }
    }
}
