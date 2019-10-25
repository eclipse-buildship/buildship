/*******************************************************************************
 * Copyright (c) 2019 Gradle Inc.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.core.internal.configuration;

import org.eclipse.core.resources.IProject;

import org.eclipse.buildship.core.internal.event.Event;

/**
 * Event raised when the Gradle nature is removed from a project.
 *
 * @author Donat Csikos
 */
public final class GradleProjectNatureDeconfiguredEvent implements Event {

    private final IProject project;

    public GradleProjectNatureDeconfiguredEvent(IProject project) {
        this.project = project;
    }

    public IProject getProject() {
        return this.project;
    }
}
