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

package org.eclipse.buildship.ui.depsview;

import org.eclipse.buildship.ui.PluginImage;
import org.eclipse.buildship.ui.PluginImages;
import org.eclipse.jface.action.Action;

import com.google.common.base.Preconditions;

/**
 * An action on the {@link DependenciesView} to toggle whether or not to link the selection in the task view
 * to the selection in the Explorer views.
 */
public final class ToggleLinkToSelectionAction extends Action {

    private final DependenciesView dependenciesView;

    public ToggleLinkToSelectionAction(DependenciesView taskView) {
        super(null, AS_CHECK_BOX);
        this.dependenciesView = Preconditions.checkNotNull(taskView);

        setToolTipText("Link with the explorer views"); // TODO (donat)
        setImageDescriptor(PluginImages.LINK_TO_SELECTION.withState(PluginImage.ImageState.ENABLED).getImageDescriptor());
        setChecked(taskView.getState().isLinkToSelection());
    }

    @Override
    public void run() {
        this.dependenciesView.getState().setLinkToSelection(isChecked());
    }

}
