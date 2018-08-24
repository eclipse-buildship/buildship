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

import com.google.common.collect.ImmutableList;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.dialogs.PreferencesUtil;

import org.eclipse.buildship.core.GradleDistributionInfo;
import org.eclipse.buildship.core.projectimport.ProjectImportConfiguration;
import org.eclipse.buildship.core.util.binding.Property;
import org.eclipse.buildship.ui.preferences.GradleWorkbenchPreferencePage;
import org.eclipse.buildship.ui.util.widget.GradleDistributionGroup.DistributionChangedListener;
import org.eclipse.buildship.ui.util.widget.GradleProjectSettingsComposite;

/**
 * Page on the {@link ProjectImportWizard} declaring the used Gradle distribution and other advanced options for the imported project.
 */
public final class GradleOptionsWizardPage extends AbstractWizardPage {

    private final String pageContextInformation;

    private GradleProjectSettingsComposite gradleProjectSettingsComposite;

    public GradleOptionsWizardPage(ProjectImportConfiguration configuration) {
        this(configuration, ProjectWizardMessages.Title_GradleOptionsWizardPage, ProjectWizardMessages.InfoMessage_GradleOptionsWizardPageDefault,
                ProjectWizardMessages.InfoMessage_GradleOptionsWizardPageContext);
    }

    public GradleOptionsWizardPage(ProjectImportConfiguration configuration, String title, String defaultMessage, String pageContextInformation) {
        super("GradleOptions", title, defaultMessage, configuration, ImmutableList.<Property<?>>of(configuration.getDistributionInfo(), configuration.getGradleUserHome()));
        this.pageContextInformation = pageContextInformation;
    }

    @Override
    protected void createWidgets(Composite root) {
        GridLayoutFactory.swtDefaults().applyTo(root);
        this.gradleProjectSettingsComposite = GradleProjectSettingsComposite.builder(root)
                .withOverrideCheckbox("Override workspace settings", "Configure Workspace Settings")
                .withAutoSyncCheckbox()
                .build();

        GridDataFactory.fillDefaults().grab(true, false).applyTo(this.gradleProjectSettingsComposite);
        this.gradleProjectSettingsComposite.getParentPreferenceLink().addSelectionListener(new WorkbenchPreferenceOpeningSelectionListener());

        initValues();
        addListeners();
    }

    private void initValues() {
        this.gradleProjectSettingsComposite.getOverrideBuildSettingsCheckbox().setSelection(getConfiguration().getOverwriteWorkspaceSettings().getValue());
        this.gradleProjectSettingsComposite.getGradleDistributionGroup().setDistributionInfo(getConfiguration().getDistributionInfo().getValue());
        this.gradleProjectSettingsComposite.getGradleUserHomeGroup().setGradleUserHome(getConfiguration().getGradleUserHome().getValue());
        this.gradleProjectSettingsComposite.getBuildScansCheckbox().setSelection(getConfiguration().getBuildScansEnabled().getValue());
        this.gradleProjectSettingsComposite.getOfflineModeCheckbox().setSelection(getConfiguration().getOfflineMode().getValue());
        this.gradleProjectSettingsComposite.getAutoSyncCheckbox().setSelection(getConfiguration().getAutoSync().getValue());
        this.gradleProjectSettingsComposite.updateEnablement();
    }

    private void addListeners() {
        this.gradleProjectSettingsComposite.getOverrideBuildSettingsCheckbox().addSelectionListener(new SelectionListener() {

            @Override
            public void widgetSelected(SelectionEvent e) {
                getConfiguration().getOverwriteWorkspaceSettings().setValue(GradleOptionsWizardPage.this.gradleProjectSettingsComposite.getOverrideBuildSettingsCheckbox().getSelection());

            }

            @Override
            public void widgetDefaultSelected(SelectionEvent e) {
                getConfiguration().getOverwriteWorkspaceSettings().setValue(GradleOptionsWizardPage.this.gradleProjectSettingsComposite.getOverrideBuildSettingsCheckbox().getSelection());
            }
        });

        this.gradleProjectSettingsComposite.getGradleDistributionGroup().addDistributionChangedListener(new DistributionChangedListener() {

            @Override
            public void distributionUpdated(GradleDistributionInfo distributionInfo) {
                getConfiguration().setDistributionInfo(distributionInfo);
            }
        });

        this.gradleProjectSettingsComposite.getGradleUserHomeGroup().getGradleUserHomeText().addModifyListener(new ModifyListener() {

            @Override
            public void modifyText(ModifyEvent e) {
                getConfiguration().setGradleUserHome(GradleOptionsWizardPage.this.gradleProjectSettingsComposite.getGradleUserHomeGroup().getGradleUserHome());
            }
        });
        this.gradleProjectSettingsComposite.getBuildScansCheckbox().addSelectionListener(new SelectionListener() {

            @Override
            public void widgetSelected(SelectionEvent e) {
                getConfiguration().getBuildScansEnabled().setValue(GradleOptionsWizardPage.this.gradleProjectSettingsComposite.getBuildScansCheckbox().getSelection());

            }

            @Override
            public void widgetDefaultSelected(SelectionEvent e) {
                getConfiguration().getBuildScansEnabled().setValue(GradleOptionsWizardPage.this.gradleProjectSettingsComposite.getBuildScansCheckbox().getSelection());
            }
        });

        this.gradleProjectSettingsComposite.getOfflineModeCheckbox().addSelectionListener(new SelectionListener() {

            @Override
            public void widgetSelected(SelectionEvent e) {
                getConfiguration().getOfflineMode().setValue(GradleOptionsWizardPage.this.gradleProjectSettingsComposite.getOfflineModeCheckbox().getSelection());

            }

            @Override
            public void widgetDefaultSelected(SelectionEvent e) {
                getConfiguration().getOfflineMode().setValue(GradleOptionsWizardPage.this.gradleProjectSettingsComposite.getOfflineModeCheckbox().getSelection());
            }
        });
        this.gradleProjectSettingsComposite.getAutoSyncCheckbox().addSelectionListener(new SelectionListener() {

            @Override
            public void widgetSelected(SelectionEvent e) {
                getConfiguration().getAutoSync().setValue(GradleOptionsWizardPage.this.gradleProjectSettingsComposite.getAutoSyncCheckbox().getSelection());

            }

            @Override
            public void widgetDefaultSelected(SelectionEvent e) {
                getConfiguration().getAutoSync().setValue(GradleOptionsWizardPage.this.gradleProjectSettingsComposite.getAutoSyncCheckbox().getSelection());
            }
        });

    }

    @Override
    protected String getPageContextInformation() {
        return this.pageContextInformation;
    }

    /**
     * Opens the workspace preferences dialog.
     */
    private class WorkbenchPreferenceOpeningSelectionListener implements SelectionListener {

        @Override
        public void widgetSelected(SelectionEvent e) {
            openWorkspacePreferences();
        }

        @Override
        public void widgetDefaultSelected(SelectionEvent e) {
            openWorkspacePreferences();
        }

        private void openWorkspacePreferences() {
            PreferencesUtil.createPreferenceDialogOn(getShell(), GradleWorkbenchPreferencePage.PAGE_ID, null, null).open();
        }
    }
}
