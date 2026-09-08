/*******************************************************************************
 * Copyright (c) 2023 Gradle Inc. and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.core.internal.util.gradle;

import java.io.File;

import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.GradleTask;
import org.gradle.tooling.model.ProjectIdentifier;
import org.gradle.tooling.model.gradle.GradleScript;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;

/**
 * Compatibility decorator for {@link GradleProject}.
 *
 * @author Donat Csikos
 */
class CompatGradleProject extends CompatModelElement<GradleProject> implements GradleProject {

    public CompatGradleProject(GradleProject delegate) {
        super(delegate);
    }

    @Override
    public String getDescription() {
        return getElement().getDescription();
    }

    @Override
    public String getName() {
        return getElement().getName();
    }

    @Override
    public GradleProject findByPath(String path) {
        return getElement().findByPath(path);
    }

    @Override
    public File getBuildDirectory() {
        // If Gradle versions < 1.8 then <code>null</code> is returned
        try {
            return getElement().getBuildDirectory();
        } catch (Exception ignore) {
            return null;
        }
    }

    @Override
    public GradleScript getBuildScript() {
        // If Gradle versions < 1.8 then <code>null</code> is returned
        try {
            return getElement().getBuildScript();
        } catch (Exception ignore) {
            return null;
        }
    }

    @Override
    public DomainObjectSet<? extends GradleProject> getChildren() {
        Builder<GradleProject> result = ImmutableList.builder();
        for (GradleProject child : getElement().getChildren()) {
            result.add(new CompatGradleProject(child));
        }
        return ModelUtils.asDomainObjectSet(result.build());
    }

    @Override
    public GradleProject getParent() {
        GradleProject parent  = getElement().getParent();
        return parent == null ? parent : new CompatGradleProject(parent);
    }

    @Override
    public String getPath() {
        return getElement().getPath();
    }

    @Override
    public File getProjectDirectory() {
        // If Gradle versions < 2.4 then <code>null</code> is returned.
        try {
            return getElement().getProjectDirectory();
        } catch (Exception ignore) {
            return null;
        }
    }

    @Override
    public ProjectIdentifier getProjectIdentifier() {
        return getElement().getProjectIdentifier();
    }

    @Override
    public DomainObjectSet<? extends GradleTask> getTasks() {
        ImmutableList<GradleTask> tasks = FluentIterable.from(getElement().getTasks()).transform(new Function<GradleTask, GradleTask>() {

            @Override
            public GradleTask apply(GradleTask task) {
                return new CompatTask(task);
            }
        }).toList();
        return ModelUtils.asDomainObjectSet(tasks);
    }
}
