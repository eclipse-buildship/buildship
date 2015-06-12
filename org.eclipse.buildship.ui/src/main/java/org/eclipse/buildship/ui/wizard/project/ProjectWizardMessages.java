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

package org.eclipse.buildship.ui.wizard.project;

import org.eclipse.osgi.util.NLS;

/**
 * Lists the i18n resource keys for the project import messages.
 */
public final class ProjectWizardMessages extends NLS {

    private static final String BUNDLE_NAME = "org.eclipse.buildship.ui.wizard.project.ProjectWizardMessages"; //$NON-NLS-1$

    public static String Title_GradleWelcomeWizardPage;
    public static String Title_GradleProjectWizardPage;
    public static String Title_GradleOptionsWizardPage;
    public static String Title_PreviewImportWizardPage;

    public static String Title_NewGradleProjectWizardPage;
    public static String Title_NewGradleProjectPreviewWizardPage;

    public static String Title_Dialog_PreGradle20VersionUsed;

    public static String Label_ProjectRootDirectory;
    public static String Label_GradleUserHome;
    public static String Label_JavaHome;
    public static String Label_JvmArguments;
    public static String Label_ProgramArguments;

    public static String Label_AdvancedOptions;
    public static String Label_GradleDistribution;
    public static String Label_GradleVersion;
    public static String Label_ProjectStructure;

    public static String Label_ProjectName;
    public static String Group_Label_ProjectLocation;
    public static String Button_UseDefaultLocation;
    public static String Label_CustomLocation;
    public static String Group_Label_WorkingSets;
    public static String Message_TargetProjectDirectory;

    public static String InfoMessage_GradleWelcomeWizardPageDefault;
    public static String InfoMessage_GradleProjectWizardPageDefault;
    public static String InfoMessage_GradleOptionsWizardPageDefault;
    public static String InfoMessage_PreviewImportWizardPageDefault;

    public static String InfoMessage_NewGradleProjectWizardPageDefault;
    public static String InfoMessage_NewGradleProjectPreviewWizardPageDefault;

    public static String InfoMessage_GradleWelcomeWizardPageContext;
    public static String InfoMessage_GradleProjectWizardPageContext;
    public static String InfoMessage_GradleOptionsWizardPageContext;
    public static String InfoMessage_GradlePreviewWizardPageContext;

    public static String InfoMessage_NewGradleProjectWizardPageContext;
    public static String InfoMessage_NewGradleProjectPreviewWizardPageContext;

    public static String InfoMessage_PreGradle20VersionUsed;

    static {
        // initialize resource bundle
        NLS.initializeMessages(BUNDLE_NAME, ProjectWizardMessages.class);
    }

    private ProjectWizardMessages() {
    }

}
