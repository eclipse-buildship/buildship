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

package org.eclipse.buildship.ui.projectimport;

import com.google.common.collect.ImmutableList;

import com.gradleware.tooling.toolingutils.binding.Property;

import org.eclipse.buildship.core.projectimport.ProjectImportConfiguration;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.PlatformUI;

/**
 * First page in the {@link ProjectImportWizard} displaying a welcome message.
 */
public final class GradleWelcomeWizardPage extends AbstractWizardPage {

    private final Font headerFont;

    public GradleWelcomeWizardPage(ProjectImportConfiguration configuration) {
        super("GradleWelcome", ProjectImportMessages.Title_GradleWelcomeWizardPage, ProjectImportMessages.InfoMessage_GradleWelcomeWizardPageDefault, //$NON-NLS-1$
                configuration, ImmutableList.<Property<?>>of());
        this.headerFont = createHeaderFont();
    }

    private Font createHeaderFont() {
        FontData[] fontData = JFaceResources.getDialogFont().getFontData();
        for (FontData font : fontData) {
            font.setHeight(18);
        }
        return new Font(PlatformUI.getWorkbench().getDisplay(), fontData);
    }

    @Override
    protected void createWidgets(Composite root) {
        root.setLayout(new GridLayout(1, false));

        StyledText welcomeText = new StyledText(root, SWT.WRAP | SWT.MULTI | SWT.CENTER);
        GridData welcomeLayoutData = new GridData(SWT.CENTER, SWT.TOP, false, false, 1, 1);
        welcomeLayoutData.widthHint = 500;
        welcomeText.setLayoutData(welcomeLayoutData);
        welcomeText.setBackground(welcomeText.getParent().getBackground());
        welcomeText.setEnabled(false);
        welcomeText.setEditable(false);
        fillWelcomeText(welcomeText);
    }

    private void fillWelcomeText(StyledText welcome) {
        String headerText = "How to experience the best Gradle integration";
        String welcomeText = headerText + "\n\nPoint the wizard to the root location of the Gradle project to import. " +
                "Buildship will take care of importing all the belonging projects. All imported projects that already contain " +
                "an Eclipse .project file will be left alone, aside from being added the Gradle nature.\n\n" +
                "You will experience the best Gradle integration, if you make use of the Gradle wrapper in your Gradle build and configure it " +
                "to use the latest released version of Gradle. Using the Gradle wrapper also makes the build most sharable between multiple users.\n\n" +
                "Unless you have a very specific reason, leave the advanced options at their default values.";
        welcome.setText(welcomeText);

        StyleRange headerStyle = new StyleRange();
        headerStyle.start = 0;
        headerStyle.length = headerText.length();
        headerStyle.font = this.headerFont;
        headerStyle.fontStyle = SWT.BOLD;
        welcome.setStyleRange(headerStyle);
    }

    @Override
    protected String getPageContextInformation() {
        return ProjectImportMessages.InfoMessage_GradleWelcomeWizardPageContext;
    }

    @Override
    public void dispose() {
        this.headerFont.dispose();
        super.dispose();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);

        // if the welcome page is visible, disable the Finish button
        ((ProjectImportWizard) getWizard()).setFinishGloballyEnabled(!visible);
    }

}
