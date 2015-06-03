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

import java.io.File;

import com.google.common.collect.ImmutableList;

import com.gradleware.tooling.toolingutils.binding.Property;

import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;

import org.eclipse.buildship.core.projectimport.ProjectImportConfiguration;
import org.eclipse.buildship.core.util.file.FileUtils;
import org.eclipse.buildship.ui.util.file.DirectoryDialogSelectionListener;
import org.eclipse.buildship.ui.util.layout.LayoutUtils;
import org.eclipse.buildship.ui.util.widget.UiBuilder;

/**
 * Page in the {@link ProjectImportWizard} specifying the Gradle root project folder to import.
 */
public final class GradleProjectWizardPage extends AbstractWizardPage {

    private Text projectDirText;
    private TextDecoratingValidationListener projectDirTextDecoratingValidator;

    public GradleProjectWizardPage(ProjectImportConfiguration configuration) {
        super("GradleProject", ProjectWizardMessages.Title_GradleProjectWizardPage, ProjectWizardMessages.InfoMessage_GradleProjectWizardPageDefault, //$NON-NLS-1$
                configuration, ImmutableList.<Property<?>> of(configuration.getProjectDir()));
    }

    @Override
    protected void createWidgets(Composite root) {
        root.setLayout(LayoutUtils.newGridLayout(3));
        createContent(root);
        bindToConfiguration();
    }

    private void createContent(Composite root) {
        UiBuilder.UiBuilderFactory uiBuilderFactory = getUiBuilderFactory();

        // project directory label
        uiBuilderFactory.newLabel(root).alignLeft().text(ProjectWizardMessages.Label_ProjectRootDirectory).control();

        // project directory text field
        File projectDir = getConfiguration().getProjectDir().getValue();
        String projectDirValue = FileUtils.getAbsolutePath(projectDir).orNull();
        this.projectDirText = uiBuilderFactory.newText(root).alignFillHorizontal().text(projectDirValue).control();
        this.projectDirTextDecoratingValidator = TextDecoratingValidationListener.newInstance(this.projectDirText);

        // browse button for file chooser
        Button projectDirBrowseButton = uiBuilderFactory.newButton(root).alignLeft().text(ProjectWizardMessages.Button_Label_Browse).control();
        projectDirBrowseButton.addSelectionListener(new DirectoryDialogSelectionListener(root.getShell(), this.projectDirText, ProjectWizardMessages.Label_ProjectRootDirectory));
    }

    private void bindToConfiguration() {
        this.projectDirText.addModifyListener(new ModifyListener() {

            @Override
            public void modifyText(ModifyEvent e) {
                getConfiguration().setProjectDir(FileUtils.getAbsoluteFile(GradleProjectWizardPage.this.projectDirText.getText()).orNull());
            }
        });

        getConfiguration().getProjectDir().addValidationListener(this.projectDirTextDecoratingValidator);
    }

    @Override
    protected String getPageContextInformation() {
        return ProjectWizardMessages.InfoMessage_GradleProjectWizardPageContext;
    }

}
