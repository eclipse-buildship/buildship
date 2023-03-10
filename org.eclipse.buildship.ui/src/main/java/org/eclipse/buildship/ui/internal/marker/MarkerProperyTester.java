/*******************************************************************************
 * Copyright (c) 2019 Gradle Inc. and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.ui.internal.marker;

import org.eclipse.core.resources.IMarker;

import org.eclipse.buildship.core.internal.GradlePluginsRuntimeException;
import org.eclipse.buildship.core.internal.marker.GradleErrorMarker;

/**
 * Property tester to determine if the test launch shortcut should be visible in the context menus.
 */
public final class MarkerProperyTester extends org.eclipse.core.expressions.PropertyTester {

    private static final String PROPERTY_NAME_IS_GRADLE_MARKER = "gradlemarker";

    @Override
    public boolean test(Object receiver, String propertyString, Object[] args, Object expectedValue) {
        if (propertyString.equals(PROPERTY_NAME_IS_GRADLE_MARKER)) {
            return receiver instanceof IMarker && isGradleMarker((IMarker)receiver);
        } else {
            throw new GradlePluginsRuntimeException("Unrecognized property to test: " + propertyString);
        }
    }

    private boolean isGradleMarker(IMarker marker) {
        try {
            return marker.getType().equals(GradleErrorMarker.ID);
        } catch (Exception e) {
            return false;
        }
    }

}

