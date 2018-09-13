/*
 * Copyright (c) 2018 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.buildship.core;

import java.io.File;

import org.gradle.tooling.GradleConnector;

import com.google.common.base.Preconditions;

/**
 * A reference to a local Gradle installation. The appropriate distribution is downloaded and
 * installed into the user's Gradle home directory.
 *
 * @author Donat Csikos
 * @since 3.0
 * @noimplement this interface is not intended to be implemented by clients
 */
public final class LocalGradleDistribution extends GradleDistribution {

    private final File location;

    LocalGradleDistribution(File location) {
        this.location = Preconditions.checkNotNull(location);
    }

    /**
     * The directory containing a Gradle installation.
     *
     * @return the Gradle distribution location
     */
    public File getLocation() {
        return this.location;
    }

    @Override
    public void apply(GradleConnector connector) {
        connector.useInstallation(this.location);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((this.location == null) ? 0 : this.location.hashCode());
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
        LocalGradleDistribution other = (LocalGradleDistribution) obj;
        if (this.location == null) {
            if (other.location != null) {
                return false;
            }
        } else if (!this.location.equals(other.location)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return String.format("GRADLE_DISTRIBUTION(LOCAL_INSTALLATION(%s))", this.location.getPath());
    }
}
