/*
 * Copyright (c) 2016 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.eclipse.buildship.core.extensions.result;

import java.util.List;

/**
 * Provides access to external plugin contributions.
 *
 * @author Donat Csikos
 */
public interface ContributionManager {

    /**
     * Returns all extra arguments contributed via the
     * {@code org.eclipse.buildship.core.invocationcustomizers} extension point.
     *
     * @return the contributed arguments
     */
    public List<String> getContributedExtraArguments();
}
