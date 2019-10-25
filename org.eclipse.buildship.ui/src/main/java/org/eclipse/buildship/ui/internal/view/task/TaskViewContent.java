/*******************************************************************************
 * Copyright (c) 2019 Gradle Inc.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.ui.internal.view.task;

import java.util.List;

import org.gradle.tooling.model.eclipse.EclipseProject;

import org.eclipse.core.resources.IProject;

/**
 * Encapsulates the content backing the {@link TaskView}.
 */
public final class TaskViewContent {

    private final List<EclipseProject> projects;
    private final List<IProject> faultyProjects;

    public TaskViewContent(List<EclipseProject> projects, List<IProject> faultyProjects) {
        this.projects = projects;
        this.faultyProjects = faultyProjects;
    }

    public List<EclipseProject> getProjects() {
        return this.projects;
    }

    public List<IProject> getFaultyProjects() {
        return this.faultyProjects;
    }
}
