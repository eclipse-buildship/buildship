/*******************************************************************************
 * Copyright (c) 2023 Gradle Inc. and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.ui.internal.i18n;

import org.eclipse.osgi.util.NLS;

/**
 * Lists the i18n resource keys for the ui messages.
 */
public final class UiMessages extends NLS {

    private static final String BUNDLE_NAME = "org.eclipse.buildship.ui.internal.i18n.UiMessages"; //$NON-NLS-1$

    public static String Title_Select_0;
    public static String Title_Gradle_Build_Script_Compare;

    public static String Button_Label_Browse;

    public static String Action_FilterFailedTasks_Tooltip;
    public static String Action_ExpandNodes_Tooltip;
    public static String Action_CollapseNodes_Tooltip;
    public static String Action_ShowFilter_Tooltip;
    public static String Action_CancelExecution_Tooltip;
    public static String Action_RerunBuild_Tooltip;
    public static String Action_RerunFailedTests_Tooltip;
    public static String Action_NoActionsAvailable_Label;

    public static String Button_CopyFailuresToClipboard_Tooltip;

    public static String Dialog_Title_Multiple_Errors;

    static {
        // initialize resource bundle
        NLS.initializeMessages(BUNDLE_NAME, UiMessages.class);
    }

    private UiMessages() {
    }

}
