/*******************************************************************************
 * Copyright (c) 2019 Gradle Inc.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.core.internal.preferences;

import org.eclipse.core.resources.IProject;

/**
 * Project-scoped storage holding Gradle model information in workspace plugin state area.
 * <p/>
 * The service should be accessed via {code CorePlugin#modelPersistence()}.
 *
 * @author Donat Csikos
 */
public interface ModelPersistence {

    /**
     * Reads the project model and returns a snapshot of the current state. If no model has been
     * saved for the project then an empty model is returned. For an empty model the
     * {@link PersistentModel#isPresent()} method returns {@code false} accessing attributes result
     * in a runtime exception.
     *
     * @param project the target project
     * @return the model
     */
    PersistentModel loadModel(IProject project);

    /**
     * Saves the project model.
     *
     * @param model the model to save
     */
    void saveModel(PersistentModel model);

    /**
     * Removes the model associated to the target project.
     *
     * @param project the target project
     */
    void deleteModel(IProject project);
}
