/*
 * Copyright (c) 2016 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 */
package org.eclipse.buildship.ui.workspace;

import java.util.List;
import java.util.Set;

import com.google.common.collect.Sets;

import com.gradleware.tooling.toolingclient.GradleDistribution;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.ui.handlers.HandlerUtil;

import org.eclipse.buildship.core.CorePlugin;
import org.eclipse.buildship.core.configuration.BuildConfiguration;
import org.eclipse.buildship.core.configuration.GradleProjectNature;
import org.eclipse.buildship.core.util.collections.AdapterFunction;
import org.eclipse.buildship.core.workspace.GradleBuild;
import org.eclipse.buildship.core.workspace.NewProjectHandler;

/**
 * Synchronizes the given projects as if the user had run the import wizard on their location.
 *
 * @author Stefan Oehme
 *
 */
public class AddBuildshipNatureHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        ISelection selection = HandlerUtil.getCurrentSelection(event);
        if (selection instanceof StructuredSelection) {
            List<?> elements = ((StructuredSelection) selection).toList();
            Set<BuildConfiguration> buildConfigs = collectBuildConfigs(elements);
            synchronize(buildConfigs);
        }
        return null;
    }

    private Set<BuildConfiguration> collectBuildConfigs(List<?> elements) {
        Set<BuildConfiguration> buildConfigs = Sets.newLinkedHashSet();
        AdapterFunction<IProject> adapterFunction = AdapterFunction.forType(IProject.class);
        for (Object element : elements) {
            IProject project = adapterFunction.apply(element);
            if (project != null && !GradleProjectNature.isPresentOn(project)) {
                IPath location = project.getLocation();
                if (location != null) {
                    buildConfigs.add(CorePlugin.configurationManager().createBuildConfiguration(location.toFile(), GradleDistribution.fromBuild(), false, false, false));
                }
            }

        }
        return buildConfigs;
    }

    private void synchronize(Set<BuildConfiguration> buildConfigs) {
        for (final BuildConfiguration buildConfig : buildConfigs) {
            GradleBuild gradleBuild = CorePlugin.gradleWorkspaceManager().getGradleBuild(buildConfig);
            gradleBuild.synchronize(NewProjectHandler.IMPORT_AND_MERGE);
        }
    }

}
