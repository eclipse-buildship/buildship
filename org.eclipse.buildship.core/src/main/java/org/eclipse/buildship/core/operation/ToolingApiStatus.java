/*
 * Copyright (c) 2017 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.eclipse.buildship.core.operation;

import java.util.List;

import org.gradle.tooling.BuildCancelledException;
import org.gradle.tooling.BuildException;
import org.gradle.tooling.GradleConnectionException;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;

import org.eclipse.buildship.core.CorePlugin;
import org.eclipse.buildship.core.GradlePluginsRuntimeException;
import org.eclipse.buildship.core.UnsupportedConfigurationException;
import org.eclipse.buildship.core.util.string.StringUtils;
import org.eclipse.buildship.core.workspace.internal.ImportRootProjectOperation.ImportRootProjectException;

/**
 * Custom {@link IStatus} implementation to represent Gradle-related statuses.
 *
 * @author Donat Csikos
 */
public final class ToolingApiStatus extends Status implements IStatus {

    /**
     * The possible problem types that a {@link ToolingApiStatus} can represent.
     */
    public static enum ToolingApiStatusType {

        BUILD_CANCELLED(IStatus.CANCEL, "%s"),
        IMPORT_ROOT_DIR_FAILED(IStatus.WARNING, "%s failed due to an error while importing the root project."),
        BUILD_FAILED(IStatus.WARNING, "%s failed due to an error in the referenced Gradle build."),
        CONNECTION_FAILED(IStatus.WARNING, "%s failed due to an error connecting to the Gradle build."),
        UNSUPPORTED_CONFIGURATION(IStatus.WARNING, "%s failed due to an unsupported configuration in the referenced Gradle build."),
        PLUGIN_FAILED(IStatus.ERROR, "%s failed due to an error configuring Eclipse."),
        UNKNOWN(IStatus.ERROR, "%s failed due to an unexpected error.");

        private final int severity;
        private final String messageTemplate;

        private ToolingApiStatusType(int severity, String messageTemplate) {
            this.severity = severity;
            this.messageTemplate = messageTemplate;
        }

        public int getSeverity() {
            return this.severity;
        }

        public int getCode() {
            return this.ordinal();
        }

        public boolean matches(ToolingApiStatus status) {
            return getCode() == status.getCode();
        }

        String messageTemplate() {
            return this.messageTemplate;
        }
    }

    private String workName;

    private ToolingApiStatus(ToolingApiStatusType type, String workName, Throwable exception) {
        super(type.getSeverity(), CorePlugin.PLUGIN_ID, type.getCode(), String.format(type.messageTemplate(), workName), exception);
        this.workName = workName;
    }

    public static ToolingApiStatus from(String workName, Throwable failure) {
        if (failure instanceof OperationCanceledException) {
            return new ToolingApiStatus(ToolingApiStatusType.BUILD_CANCELLED, workName, null);
        } else if (failure instanceof BuildCancelledException) {
            return new ToolingApiStatus(ToolingApiStatusType.BUILD_CANCELLED, workName, null);
        } else if (failure instanceof BuildException) {
            return new ToolingApiStatus(ToolingApiStatusType.BUILD_FAILED, workName, (BuildException) failure);
        } else if (failure instanceof GradleConnectionException) {
            return new ToolingApiStatus(ToolingApiStatusType.CONNECTION_FAILED, workName, (GradleConnectionException) failure);
        } else if (failure instanceof ImportRootProjectException) {
            return new ToolingApiStatus(ToolingApiStatusType.IMPORT_ROOT_DIR_FAILED, workName, (ImportRootProjectException) failure);
        } else if (failure instanceof UnsupportedConfigurationException) {
            return new ToolingApiStatus(ToolingApiStatusType.UNSUPPORTED_CONFIGURATION, workName, (UnsupportedConfigurationException) failure);
        } else if (failure instanceof GradlePluginsRuntimeException) {
            return new ToolingApiStatus(ToolingApiStatusType.PLUGIN_FAILED, workName, (GradlePluginsRuntimeException) failure);
        } else {
            return new ToolingApiStatus(ToolingApiStatusType.UNKNOWN, workName, failure);
        }
    }

    /**
     * Default way of presenting {@link ToolingApiStatus} instances.
     * <p>
     * TODO this method should disappear once we successfully convert all error dialogs
     * displays to markers and log messages.
     *
     * @param workName The name of the task to display in the error dialog
     * @param status the status to present in the dialog
     */

    public void handleDefault() {
        CorePlugin.getInstance().getLog().log(this);

        if (severityMatches(IStatus.WARNING | IStatus.ERROR)) {
            CorePlugin.userNotification().errorOccurred(
                    String.format("%s failed", this.workName),
                    getMessage(),
                    collectErrorMessages(getException()),
                    getSeverity(),
                    getException());
        }
    }

    public boolean severityMatches(int severity) {
        return (getSeverity() & severity) != 0;
    }

    private static String collectErrorMessages(Throwable t) {
        if (t == null) {
            return "";
        }

        // recursively collect the error messages going up the stacktrace
        // avoid the same message showing twice in a row
        List<String> messages = Lists.newArrayList();
        Throwable cause = t.getCause();
        if (cause != null) {
            collectCausesRecursively(cause, messages);
        }
        String messageStack = Joiner.on('\n').join(StringUtils.removeAdjacentDuplicates(messages));
        return t.getMessage() + (messageStack.isEmpty() ? "" : "\n\n" + messageStack);
    }

    private static void collectCausesRecursively(Throwable t, List<String> messages) {
        List<String> singleLineMessages = Splitter.on('\n').omitEmptyStrings().splitToList(Strings.nullToEmpty(t.getMessage()));
        messages.addAll(singleLineMessages);
        Throwable cause = t.getCause();
        if (cause != null) {
            collectCausesRecursively(cause, messages);
        }
    }
}
