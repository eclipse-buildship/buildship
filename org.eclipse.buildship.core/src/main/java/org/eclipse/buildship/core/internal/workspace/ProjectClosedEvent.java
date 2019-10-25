/*******************************************************************************
 * Copyright (c) 2019 Gradle Inc.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.core.internal.workspace;

import org.eclipse.core.resources.IProject;

/**
 * Project closed event.
 *
 * @author Prashanth Rao
 */
public final class ProjectClosedEvent extends BaseProjectChangedEvent {

    public ProjectClosedEvent(IProject project) {
       super(project);
    }
}
