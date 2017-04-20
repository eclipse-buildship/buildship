/*
 * Copyright (c) 2017 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.eclipse.buildship.core.configuration.internal;

import java.io.File;
import java.util.List;

import com.google.common.base.Objects;

/**
 * Properties backing a {@code RunConfiguration} instance.
 *
 * @author Donat Csikos
 */
final class RunConfigurationProperties {

    private final List<String> tasks;
    private final File javaHome;
    private final List<String> jvmArguments;
    private final List<String> arguments;
    private final boolean showConsoleView;
    private final boolean showExecutionsView;

    public RunConfigurationProperties(List<String> tasks, File javaHome, List<String> jvmArguments, List<String> arguments, boolean showConsoleView, boolean showExecutionsView) {
        this.tasks = tasks;
        this.javaHome = javaHome;
        this.jvmArguments = jvmArguments;
        this.arguments = arguments;
        this.showConsoleView = showConsoleView;
        this.showExecutionsView = showExecutionsView;
    }

    public List<String> getTasks() {
        return this.tasks;
    }

    public File getJavaHome() {
        return this.javaHome;
    }

    public List<String> getJvmArguments() {
        return this.jvmArguments;
    }

    public List<String> getArguments() {
        return this.arguments;
    }

    public boolean isShowConsoleView() {
        return this.showConsoleView;
    }

    public boolean isShowExecutionView() {
        return this.showExecutionsView;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof RunConfigurationProperties) {
            RunConfigurationProperties other = (RunConfigurationProperties) obj;
            return Objects.equal(this.tasks, other.tasks) && Objects.equal(this.javaHome, other.javaHome) && Objects.equal(this.jvmArguments, other.jvmArguments)
                    && Objects.equal(this.arguments, other.arguments) && Objects.equal(this.showConsoleView, other.showConsoleView)
                    && Objects.equal(this.showExecutionsView, other.showExecutionsView);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.tasks, this.javaHome, this.jvmArguments, this.arguments, this.showConsoleView, this.showExecutionsView);
    }
}