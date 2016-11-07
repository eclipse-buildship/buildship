/*
 * Copyright (c) 2016 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.buildship.core.configuration.internal;

import java.io.File;

import org.osgi.service.prefs.BackingStoreException;

import com.google.common.base.Preconditions;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;

import org.eclipse.buildship.core.CorePlugin;
import org.eclipse.buildship.core.GradlePluginsRuntimeException;
import org.eclipse.buildship.core.configuration.WorkspaceConfiguration;
import org.eclipse.buildship.core.configuration.WorkspaceConfigurationManager;

/**
 * The default implementation of {@link WorkspaceConfigurationManager}.
 *
 * @author Stefan oehme
 */
public class DefaultWorkspaceConfigurationManager implements WorkspaceConfigurationManager {

    private static final String GRADLE_USER_HOME = "gradle.user.home";
    private static final String GRADLE_OFFLINE_MODE = "gradle.offline.mode";

    @Override
    public WorkspaceConfiguration loadWorkspaceConfiguration() {
        IEclipsePreferences preferences = getPreferences();
        String userHome = preferences.get(GRADLE_USER_HOME, null);
        boolean offlineMode = preferences.getBoolean(GRADLE_OFFLINE_MODE, false);
        return new WorkspaceConfiguration(userHome == null ? null : new File(userHome), offlineMode);
    }

    @Override
    public void saveWorkspaceConfiguration(WorkspaceConfiguration config) {
        Preconditions.checkNotNull(config);
        IEclipsePreferences preferences = getPreferences();
        if (config.getGradleUserHome() == null) {
            preferences.remove(GRADLE_USER_HOME);
        } else {
            preferences.put(GRADLE_USER_HOME, config.getGradleUserHome().getPath());
        }
        preferences.putBoolean(GRADLE_OFFLINE_MODE, config.isOffline());
        try {
            preferences.flush();
        } catch (BackingStoreException e) {
            throw new GradlePluginsRuntimeException("Could not persist workspace preferences", e);
        }
    }

    private IEclipsePreferences getPreferences() {
        return InstanceScope.INSTANCE.getNode(CorePlugin.PLUGIN_ID);
    }

}
