/*******************************************************************************
 * Copyright (c) 2023 Gradle Inc. and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.ui.internal.view.execution;

import org.osgi.service.prefs.BackingStoreException;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;

import org.eclipse.buildship.core.internal.util.preference.EclipsePreferencesUtils;
import org.eclipse.buildship.ui.internal.UiPlugin;

/**
 * Represents the (persistable) configuration state of the {@link ExecutionsView}. Backed by the
 * Eclipse Preferences API.
 */
public final class ExecutionViewState {

    private static final String PREF_HEADER_NAME_COLUMN_WIDTH = "executionsView.headerNameColumnWidth"; //$NON-NLS-1$
    private static final String PREF_HEADER_DURATION_COLUMN_WIDTH = "executionsView.headerDurationColumnWidth"; //$NON-NLS-1$

    private int headerNameColumnWidth;
    private int headerDurationColumnWidth;

    public void load() {
        IEclipsePreferences prefs = EclipsePreferencesUtils.getInstanceScope().getNode(UiPlugin.PLUGIN_ID);
        this.headerNameColumnWidth = prefs.getInt(PREF_HEADER_NAME_COLUMN_WIDTH, 600);
        this.headerDurationColumnWidth = prefs.getInt(PREF_HEADER_DURATION_COLUMN_WIDTH, 100);
    }

    public void save() {
        IEclipsePreferences prefs = EclipsePreferencesUtils.getInstanceScope().getNode(UiPlugin.PLUGIN_ID);
        // minimum width is 1, so that the columns are resizeable by the user
       prefs.putInt(PREF_HEADER_NAME_COLUMN_WIDTH, Math.max(1, this.headerNameColumnWidth));
       prefs.putInt(PREF_HEADER_DURATION_COLUMN_WIDTH, Math.max(1, this.headerDurationColumnWidth));

        try {
            prefs.flush();
        } catch (BackingStoreException e) {
            UiPlugin.logger().error("Unable to store execution view preferences.", e); //$NON-NLS-1$
        }
    }

    public int getHeaderNameColumnWidth() {
        return this.headerNameColumnWidth;
    }

    public void setHeaderNameColumnWidth(int headerNameColumnWidth) {
        this.headerNameColumnWidth = headerNameColumnWidth;
    }

    public int getHeaderDurationColumnWidth() {
        return this.headerDurationColumnWidth;
    }

    public void setHeaderDurationColumnWidth(int headerDurationColumnWidth) {
        this.headerDurationColumnWidth = headerDurationColumnWidth;
    }

    public void dispose() {
        save();
    }

}
