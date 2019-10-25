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

import org.eclipse.buildship.core.internal.GradlePluginsRuntimeException;
import org.eclipse.buildship.ui.internal.UiPlugin;
import org.eclipse.core.runtime.preferences.ConfigurationScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.jface.wizard.Wizard;
import org.osgi.service.prefs.BackingStoreException;

import com.google.common.base.Preconditions;

/**
 * Base class for project wizards.
 */
public abstract class AbstractProjectWizard extends Wizard implements HelpContextIdProvider {

    // the preference key under which it is stored whether to show the welcome page or not
    private final String welcomePageEnabledPreferenceKey;

    // state bit storing that the wizard is blocked to finish globally
    private boolean finishGloballyEnabled;

    protected AbstractProjectWizard(String welcomePageEnabledPreferenceKey) {
        this.welcomePageEnabledPreferenceKey = Preconditions.checkNotNull(welcomePageEnabledPreferenceKey);

        // the wizard must not be finishable unless this global flag is enabled
        this.finishGloballyEnabled = true;
    }

    public boolean isShowWelcomePage() {
        // store the in the configuration scope to have the same settings for
        // all workspaces
        @SuppressWarnings("deprecation")
        ConfigurationScope configurationScope = new ConfigurationScope();
        IEclipsePreferences node = configurationScope.getNode(UiPlugin.PLUGIN_ID);
        return node.getBoolean(this.welcomePageEnabledPreferenceKey, true);
    }

    public void setWelcomePageEnabled(boolean enabled) {
        @SuppressWarnings("deprecation")
        ConfigurationScope configurationScope = new ConfigurationScope();
        IEclipsePreferences node = configurationScope.getNode(UiPlugin.PLUGIN_ID);
        node.putBoolean(this.welcomePageEnabledPreferenceKey, enabled);
        try {
            node.flush();
        } catch (BackingStoreException e) {
            throw new GradlePluginsRuntimeException(e);
        }
    }

    @Override
    public boolean canFinish() {
        // the wizard can finish if all pages are complete and the finish is
        // globally enabled
        return super.canFinish() && this.finishGloballyEnabled;
    }

    public void setFinishGloballyEnabled(boolean finishGloballyEnabled) {
        this.finishGloballyEnabled = finishGloballyEnabled;
    }

}
