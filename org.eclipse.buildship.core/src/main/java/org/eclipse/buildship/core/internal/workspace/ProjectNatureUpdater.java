/*******************************************************************************
 * Copyright (c) 2019 Gradle Inc. and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.core.internal.workspace;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.gradle.tooling.model.eclipse.EclipseProjectNature;

import com.google.common.collect.Sets;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.buildship.core.internal.CorePlugin;
import org.eclipse.buildship.core.internal.configuration.GradleProjectNature;
import org.eclipse.buildship.core.internal.preferences.PersistentModel;
import org.eclipse.buildship.core.internal.workspace.ManagedModelMergingStrategy.Result;

/**
 * Updates the natures on the target project.
 */
final class ProjectNatureUpdater {

    private ProjectNatureUpdater() {
    }

    public static void update(IProject project, List<EclipseProjectNature> projectNatures, PersistentModelBuilder persistentModel, IProgressMonitor monitor) throws CoreException {
        PersistentModel previousPersistentModel = persistentModel.getPrevious();
        Set<String> managedNatures = previousPersistentModel.isPresent() ? Sets.newLinkedHashSet(previousPersistentModel.getManagedNatures()) : Sets.<String>newLinkedHashSet();

        Set<String> modelNatures = toNatures(projectNatures);
        IProjectDescription description = project.getDescription();
        Set<String> existingNatures = Sets.newLinkedHashSet(Arrays.asList(description.getNatureIds()));

        Result<String> result = ManagedModelMergingStrategy.calculate(existingNatures, modelNatures, managedNatures);

        description.setNatureIds(result.getNextElements().toArray(new String[0]));
        project.setDescription(description, monitor);
        persistentModel.managedNatures(result.getNextManaged());
    }

    private static Set<String> toNatures(List<EclipseProjectNature> projectNatures) {
        Set<String> natures = Sets.newLinkedHashSet();
        for (EclipseProjectNature projectNature : projectNatures) {
            String natureId = projectNature.getId();
            if (CorePlugin.workspaceOperations().isNatureRecognizedByEclipse(natureId)) {
                natures.add(natureId);
            }
        }
        natures.add(GradleProjectNature.ID);
        return natures;
    }
}
