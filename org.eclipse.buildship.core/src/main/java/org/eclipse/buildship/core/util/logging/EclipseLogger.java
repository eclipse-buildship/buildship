/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Etienne Studer & Donát Csikós (Gradle Inc.) - initial API and implementation and initial documentation
 */

package org.eclipse.buildship.core.util.logging;

import org.eclipse.buildship.core.Logger;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

/**
 * Logs to the Eclipse logging infrastructure. Only logs debug information if tracing is enabled.
 *
 * Tracing can be enabled in Eclipse's 'Tracing' tab in the 'Run Configurations...' dialog.
 */
public final class EclipseLogger implements Logger {

    private final ILog log;
    private final String pluginId;
    private final boolean isDebugging;

    public EclipseLogger(ILog log, String pluginId, boolean isDebugging) {
        this.log = log;
        this.pluginId = pluginId;
        this.isDebugging = isDebugging;
    }

    @Override
    public void debug(String message) {
        if (this.isDebugging) {
            this.log.log(new Status(IStatus.INFO, this.pluginId, message));
        }
    }

    @Override
    public void debug(String message, Throwable t) {
        if (this.isDebugging) {
            this.log.log(new Status(IStatus.INFO, this.pluginId, message, t));
        }
    }

    @Override
    public void info(String message) {
        this.log.log(new Status(IStatus.INFO, this.pluginId, message));
    }

    @Override
    public void info(String message, Throwable t) {
        this.log.log(new Status(IStatus.INFO, this.pluginId, message, t));
    }

    @Override
    public void warn(String message) {
        this.log.log(new Status(IStatus.WARNING, this.pluginId, message));
    }

    @Override
    public void warn(String message, Throwable t) {
        this.log.log(new Status(IStatus.WARNING, this.pluginId, message, t));
    }

    @Override
    public void error(String message) {
        this.log.log(new Status(IStatus.ERROR, this.pluginId, message));
    }

    @Override
    public void error(String message, Throwable t) {
        this.log.log(new Status(IStatus.ERROR, this.pluginId, message, t));
    }

}
