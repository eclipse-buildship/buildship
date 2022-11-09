/*******************************************************************************
 * Copyright (c) 2019 Gradle Inc.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.ui.internal.wizard.project;

import org.eclipse.osgi.util.NLS;

/**
 * Lists the i18n resource keys for the project import messages.
 */
public final class ProjectWizardMessages extends NLS {

    private static final String BUNDLE_NAME = "org.eclipse.buildship.ui.internal.wizard.project.ProjectWizardMessages"; //$NON-NLS-1$

    public static String Title_GradleProjectWizardPage;
    public static String Title_GradleOptionsWizardPage;
    public static String Title_PreviewImportWizardPage;

    public static String Title_NewGradleProjectWizardPage;
    public static String Title_NewGradleProjectOptionsWizardPage;
    public static String Title_NewGradleProjectPreviewWizardPage;

    public static String Label_ProjectRootDirectory;
    public static String Label_GradleUserHome;
    public static String Label_JavaHome;

    public static String Label_GradleDistribution;
    public static String Label_GradleVersion;
    public static String Label_ProjectStructure;

    public static String Label_ProjectName;
    public static String Group_Label_ProjectLocation;
    public static String Button_UseDefaultLocation;
    public static String Label_CustomLocation;
    public static String Group_Label_WorkingSets;
    public static String Message_TargetProjectDirectory;

    public static String InfoMessage_GradleProjectWizardPageDefault;
    public static String InfoMessage_GradleOptionsWizardPageDefault;
    public static String InfoMessage_GradlePreviewWizardPageDefault;

    public static String InfoMessage_NewGradleProjectWizardPageDefault;
    public static String InfoMessage_NewGradleProjectOptionsWizardPageDefault;
    public static String InfoMessage_NewGradleProjectPreviewWizardPageDefault;

    public static String Title_Dialog_Missing_Features;
    public static String Missing_Features_Tooltip;
    public static String Missing_Features_Details_0_1;

    public static String Title_Dialog_PreviewStructureInfo;
    public static String PreviewStructureInfo_Tooltip;
    public static String PreviewStructureInfo_Details;

    public static String Preview_Failed;
    public static String Preview_No_Stacktrace;
    public static String Preview_Cancelled;


    static {
        // initialize resource bundle
        NLS.initializeMessages(BUNDLE_NAME, ProjectWizardMessages.class);
    }

    private ProjectWizardMessages() {
    }

}
