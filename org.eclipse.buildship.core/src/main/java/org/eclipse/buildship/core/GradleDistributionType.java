/*
 * Copyright (c) 2018 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.eclipse.buildship.core;

/**
 * Enumerates the different types of Gradle distributions.
 * TODO (donat) add reference to usages and explain constraints
 *
 * @author Donat Csikos
 * @since 3.0
 */
public enum GradleDistributionType {
    INVALID, // TODO (donat) this entry leaked to the API and should be removed
    WRAPPER,
    LOCAL_INSTALLATION,
    REMOTE_DISTRIBUTION,
    VERSION
}