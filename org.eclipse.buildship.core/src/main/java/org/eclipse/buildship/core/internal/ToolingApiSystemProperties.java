/*******************************************************************************
 * Copyright (c) 2025 Gradle Inc. and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.core.internal;

public final class ToolingApiSystemProperties {

    public static interface ThrowingRunnable {
        void run() throws Exception;
    }

    static final String ACTIVE_NAME = "buildship.active";
    static final String SYNC_ACTIVE_NAME = "buildship.sync.active";

    /**
     * Sets the {@code buildship.active} system property to {@code "true"}.
     * 
     * @return the original value of the {@code buildship.active} system property,
     *         to be passed to {@link #restoreActive(String)}.
     */
    public static String overrideActive() {
        return overrideProperty(ACTIVE_NAME, "true");
    }

    /**
     * Restores the {@code buildship.active} system property to its original value.
     * 
     * @param originalValue the original value of the {@code buildship.active}
     *                      system property obtained from {@link #overrideActive()}.
     */
    public static void restoreActive(String originalValue) {
        restoreProperty(ACTIVE_NAME, originalValue);
    }

    /**
     * Runs the given code with the {@code buildship.sync.active} system property
     * set to {@code "true"}.
     * 
     * @param <T>      the type of return produced by {@code runnable}
     * @param runnable the code to run with the system property set
     * @throws Exception when {@code runnable} throws
     */
    public static void withSyncActive(ThrowingRunnable runnable) throws Exception {
        withSystemProperty(SYNC_ACTIVE_NAME, "true", runnable);
    }

    private static synchronized void withSystemProperty(String propertyName, String value, ThrowingRunnable runnable)
            throws Exception {
        String originalValue = overrideProperty(propertyName, value);
        try {
            runnable.run();
        } finally {
            restoreProperty(propertyName, originalValue);
        }
    }

    private static String overrideProperty(String propertyName, String value) {
        String originalValue = System.getProperty(propertyName);
        if (value != null) {
            System.setProperty(propertyName, value);
        } else {
            System.clearProperty(propertyName);
        }
        return originalValue;
    }

    private static void restoreProperty(String propertyName, String originalValue) {
        if (originalValue != null) {
            System.setProperty(propertyName, originalValue);
        } else {
            System.clearProperty(propertyName);
        }
    }

    private ToolingApiSystemProperties() {
    }
}
