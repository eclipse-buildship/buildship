/*
 * Copyright (c) 2018 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.eclipse.buildship.core;

import org.gradle.tooling.GradleConnector;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

/**
 * A a reference to a specific version of Gradle. The appropriate distribution is downloaded and
 * installed into the user's Gradle home directory.
 *
 * @author Donat Csikos
 * @since 3.0
 * @noimplement this interface is not intended to be implemented by clients
 */
public final class FixedVersionGradleDistribution extends GradleDistribution {

    private final String version;

    FixedVersionGradleDistribution(String version) {
        this.version = Preconditions.checkNotNull(Strings.emptyToNull(version));
    }

    /**
     * The Gradle version to use.
     *
     * @return the Gradle version
     */
    public String getVersion() {
        return this.version;
    }

    @Override
    public void apply(GradleConnector connector) {
        connector.useGradleVersion(this.version);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((this.version == null) ? 0 : this.version.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        FixedVersionGradleDistribution other = (FixedVersionGradleDistribution) obj;
        if (this.version == null) {
            if (other.version != null) {
                return false;
            }
        } else if (!this.version.equals(other.version)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return String.format("GRADLE_DISTRIBUTION(VERSION(%s))", this.version);
    }
}
