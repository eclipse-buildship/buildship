/*
 * Copyright (c) 2018 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.eclipse.buildship.core;

import org.gradle.tooling.GradleConnector;

/**
 * Specifies to use the Gradle distribution defined by the target Gradle build. If the target build
 * has no Gradle distribution specified (i.e. no Gradle wrapper is used in the project) then the
 * Tooling API plug-in version will be picked.
 *
 * @author Donat Csikos
 * @since 3.0
 * @noimplement this interface is not intended to be implemented by clients
 */
public final class WrapperGradleDistribution extends GradleDistribution {

    WrapperGradleDistribution() {
    }

    @Override
    public void apply(GradleConnector connector) {
        connector.useBuildDistribution();
    }

    @Override
    public String toString() {
        return String.valueOf("GRADLE_DISTRIBUTION(WRAPPER)");
    }
}