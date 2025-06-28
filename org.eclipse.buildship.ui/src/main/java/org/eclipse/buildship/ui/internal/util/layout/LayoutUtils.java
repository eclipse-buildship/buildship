/*******************************************************************************
 * Copyright (c) 2023 Gradle Inc. and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.ui.internal.util.layout;

import org.eclipse.swt.layout.GridLayout;

/**
 * Contains helper methods related to UI creation.
 */
public final class LayoutUtils {

    private LayoutUtils() {
    }

    /**
     * Creates a new {@link GridLayout} with the given number of columns.
     *
     * @param numOfColumns the number of columns
     * @return the new instance
     */
    public static GridLayout newGridLayout(int numOfColumns) {
        GridLayout layout = new GridLayout(numOfColumns, false);
        layout.verticalSpacing = 0;
        return layout;
    }

    /**
     * When restoring the width of a tree column from the preferences, ensure a
     * minimum width of 1, so that the column is not completely hidden and can be
     * resized by the user.
     * 
     * @param width as read from preferences
     * @return the adjusted width
     */
    public static int ensureMinimumColumnWidth(int width) {
        return Math.max(width, 1);
    }

}
