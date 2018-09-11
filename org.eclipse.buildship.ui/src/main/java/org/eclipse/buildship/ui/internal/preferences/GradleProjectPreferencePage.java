/*
 * Copyright (c) 2017 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.eclipse.buildship.ui.internal.preferences;

import java.io.File;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Platform;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.dialogs.PropertyPage;

import org.eclipse.buildship.core.internal.CorePlugin;
import org.eclipse.buildship.core.internal.BaseGradleDistribution;
import org.eclipse.buildship.core.internal.GradleDistributionInfo;
import org.eclipse.buildship.core.internal.configuration.BuildConfiguration;
import org.eclipse.buildship.core.internal.configuration.ConfigurationManager;
import org.eclipse.buildship.core.internal.i18n.CoreMessages;
import org.eclipse.buildship.core.internal.util.binding.Validator;
import org.eclipse.buildship.core.internal.util.binding.Validators;
import org.eclipse.buildship.ui.internal.util.widget.GradleProjectSettingsComposite;
import org.eclipse.buildship.ui.internal.util.widget.GradleUserHomeGroup;

/**
 * Preference page for Gradle projects.
 *
 * @author Donat Csikos
 */
public final class GradleProjectPreferencePage extends PropertyPage {

    public static final String PAGE_ID = "org.eclipse.buildship.ui.projectproperties";

    private GradleProjectSettingsComposite gradleProjectSettingsComposite;

    private final Validator<GradleDistributionInfo> distributionInfoValidator;
    private final Validator<File> gradleUserHomeValidator;

    public GradleProjectPreferencePage() {
        this.gradleUserHomeValidator = Validators.optionalDirectoryValidator(CoreMessages.Preference_Label_GradleUserHome);
        this.distributionInfoValidator = GradleDistributionInfo.validator();
    }

    @Override
    protected Control createContents(Composite parent) {
        this.gradleProjectSettingsComposite = GradleProjectSettingsComposite.builder(parent)
                .withAutoSyncCheckbox()
                .withOverrideCheckbox("Override workspace settings", "Configure Workspace Settings")
                .build();

        initValues();
        addListeners();

        return this.gradleProjectSettingsComposite;
    }

    private void initValues() {
        IProject project = getTargetProject();
        BuildConfiguration buildConfig = CorePlugin.configurationManager().loadProjectConfiguration(project).getBuildConfiguration();
        boolean overrideWorkspaceSettings = buildConfig.isOverrideWorkspaceSettings();
        this.gradleProjectSettingsComposite.getGradleDistributionGroup().setDistributionInfo(((BaseGradleDistribution) buildConfig.getGradleDistribution()).getDistributionInfo());
        this.gradleProjectSettingsComposite.getGradleUserHomeGroup().setGradleUserHome(buildConfig.getGradleUserHome());
        this.gradleProjectSettingsComposite.getOverrideBuildSettingsCheckbox().setSelection(overrideWorkspaceSettings);
        this.gradleProjectSettingsComposite.getBuildScansCheckbox().setSelection(buildConfig.isBuildScansEnabled());
        this.gradleProjectSettingsComposite.getOfflineModeCheckbox().setSelection(buildConfig.isOfflineMode());
        this.gradleProjectSettingsComposite.getAutoSyncCheckbox().setSelection(buildConfig.isAutoSync());
        this.gradleProjectSettingsComposite.updateEnablement();
    }

    private void addListeners() {
        this.gradleProjectSettingsComposite.getParentPreferenceLink().addSelectionListener(new WorkbenchPreferenceOpeningSelectionListener());
        GradleUserHomeGroup gradleUserHomeGroup = this.gradleProjectSettingsComposite.getGradleUserHomeGroup();
        gradleUserHomeGroup.getGradleUserHomeText().addModifyListener(new GradleUserHomeValidatingListener(this, gradleUserHomeGroup, this.gradleUserHomeValidator));
        this.gradleProjectSettingsComposite.getGradleDistributionGroup().addDistributionChangedListener(new GradleDistributionInfoValidatingListener(this, this.distributionInfoValidator));
    }

    @Override
    public boolean performOk() {
       IProject project = getTargetProject();
       ConfigurationManager manager = CorePlugin.configurationManager();
       BuildConfiguration currentConfig = manager.loadProjectConfiguration(project).getBuildConfiguration();
       BuildConfiguration updatedConfig = manager.createBuildConfiguration(currentConfig.getRootProjectDirectory(),
           this.gradleProjectSettingsComposite.getOverrideBuildSettingsCheckbox().getSelection(),
           this.gradleProjectSettingsComposite.getGradleDistributionGroup().getDistributionInfo().toGradleDistribution(),
           this.gradleProjectSettingsComposite.getGradleUserHomeGroup().getGradleUserHome(),
           this.gradleProjectSettingsComposite.getBuildScansCheckbox().getSelection(),
           this.gradleProjectSettingsComposite.getOfflineModeCheckbox().getSelection(),
           this.gradleProjectSettingsComposite.getAutoSyncCheckbox().getSelection());
       manager.saveBuildConfiguration(updatedConfig);
       return true;
    }

    @SuppressWarnings({"cast", "RedundantCast"})
    private IProject getTargetProject() {
        return (IProject) Platform.getAdapterManager().getAdapter(getElement(), IProject.class);
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
