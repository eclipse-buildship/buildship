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

package org.eclipse.buildship.ui.view.task;

import com.google.common.base.Preconditions;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;

import org.eclipse.buildship.ui.PluginImage;
import org.eclipse.buildship.ui.PluginImages;

/**
 * An action on the {@link TaskView} to include the type of the task node in the sort criteria.
 *
 * @see TaskNode#getType()
 */
public final class SortTasksByTypeAction extends Action {

    private final TaskView taskView;

    public SortTasksByTypeAction(TaskView taskView) {
        super(null, IAction.AS_CHECK_BOX);
        this.taskView = Preconditions.checkNotNull(taskView);

        setText(TasksViewMessages.Action_SortByType_Text);
        setImageDescriptor(PluginImages.SORT_BY_TYPE.withState(PluginImage.ImageState.ENABLED).getImageDescriptor());
        setChecked(taskView.getState().isSortByType());
    }

    @Override
    public void run() {
        this.taskView.getState().setSortByType(isChecked());
        this.taskView.getTreeViewer().setComparator(TaskNodeViewerSorter.createFor(this.taskView.getState()));
    }

}
