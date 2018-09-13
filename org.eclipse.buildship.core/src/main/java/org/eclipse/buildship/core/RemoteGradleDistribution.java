/*
 * Copyright (c) 2018 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.eclipse.buildship.core;

import java.net.URI;

import org.gradle.tooling.GradleConnector;

import com.google.common.base.Preconditions;

/**
 * A reference to a remote Gradle distribution.
 *
 * @author Donat Csikos
 * @since 3.0
 * @noimplement this interface is not intended to be implemented by clients
 */
public final class RemoteGradleDistribution extends GradleDistribution {

    private final URI url;

    RemoteGradleDistribution(URI url) {
        this.url = Preconditions.checkNotNull(url);
    }

    /**
     * The URL pointing to the the remote Gradle distribution.
     *
     * @return the remote distribution location
     */
    public URI getUrl() {
        return this.url;
    }

    @Override
    public void apply(GradleConnector connector) {
        connector.useDistribution(this.url);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((this.url == null) ? 0 : this.url.hashCode());
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
        RemoteGradleDistribution other = (RemoteGradleDistribution) obj;
        if (this.url == null) {
            if (other.url != null) {
                return false;
            }
        } else if (!this.url.equals(other.url)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return String.format("GRADLE_DISTRIBUTION(REMOTE_DISTRIBUTION(%s))", this.url.toString());
    }
}
