/*
 * Copyright (c) 2018 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.eclipse.buildship.core.internal.extension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.google.common.annotations.VisibleForTesting;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.Platform;

import org.eclipse.buildship.core.ProjectConfigurator;
import org.eclipse.buildship.core.internal.CorePlugin;
import org.eclipse.buildship.core.invocation.InvocationCustomizer;

public class DefaultExtensionManager implements ExtensionManager {

    @Override
    public List<InvocationCustomizer> loadCustomizers() {
        Collection<IConfigurationElement> elements = loadElements("invocationcustomizers");
        List<InvocationCustomizer> result = new ArrayList<>(elements.size());
        for (IConfigurationElement element : elements) {
            try {
                result.add(InvocationCustomizer.class.cast(element.createExecutableExtension("class")));
            } catch (Exception e) {
                CorePlugin.logger().warn("Cannot load invocationcustomizers extension" , e);
            }
        }
        return result;
    }

    @Override
    public List<ProjectConfiguratorContribution> loadConfigurators() {
        Collection<IConfigurationElement> elements = loadElements("projectconfigurators");
        List<ProjectConfiguratorContribution> result = new ArrayList<>(elements.size());
        for (IConfigurationElement element : elements) {
            try {
                ProjectConfigurator configurator = ProjectConfigurator.class.cast(element.createExecutableExtension("class"));
                String pluginId = element.getContributor().getName();

                String id = element.getAttribute("id");
                if (id == null) {
                    throw new RuntimeException("Required 'id' field not declared in projectconfigurators extension from plugin " + pluginId);
                }

                ProjectConfiguratorContribution contribution = ProjectConfiguratorContribution.create(configurator, id, pluginId);

                if (result.contains(contribution)) {
                    throw new RuntimeException("Project configurator '" + contribution.getFullyQualifiedId() + "' is already declared");
                }
                result.add(contribution);
            } catch (Exception e) {
                CorePlugin.logger().warn("Cannot load projectconfigurators extension" , e);
            }
        }
        return result;
    }

    @VisibleForTesting
    Collection<IConfigurationElement> loadElements(String extensionPointName) {
        return Arrays.asList(Platform.getExtensionRegistry().getConfigurationElementsFor(CorePlugin.PLUGIN_ID, extensionPointName));
    }
}
