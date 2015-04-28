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

package org.eclipse.buildship.core.launch;

import java.io.File;
import java.util.List;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;

import com.gradleware.tooling.toolingclient.GradleDistribution;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;

import org.eclipse.buildship.core.CorePlugin;
import org.eclipse.buildship.core.GradlePluginsRuntimeException;
import org.eclipse.buildship.core.gradle.GradleDistributionSerializer;
import org.eclipse.buildship.core.i18n.CoreMessages;
import org.eclipse.buildship.core.util.file.FileUtils;
import org.eclipse.buildship.core.util.variable.ExpressionUtils;

/**
 * Contains the attributes that describe a Gradle run configuration.
 */
public final class GradleRunConfigurationAttributes {

    // keys used when setting/getting attributes from an ILaunchConfiguration instance
    private static final String TASKS = "tasks"; //$NON-NLS-1$
    private static final String WORKING_DIR = "working_dir"; //$NON-NLS-1$
    private static final String GRADLE_DISTRIBUTION = "gradle_distribution"; //$NON-NLS-1$
    private static final String GRADLE_USER_HOME = "gradle_user_home"; //$NON-NLS-1$
    private static final String JAVA_HOME = "java_home"; //$NON-NLS-1$
    private static final String JVM_ARGUMENTS = "jvm_arguments"; //$NON-NLS-1$
    private static final String ARGUMENTS = "arguments"; //$NON-NLS-1$
    private static final String VISUALIZE_TEST_PROGRESS = "visualize_test_progress"; //$NON-NLS-1$

    private final ImmutableList<String> tasks;
    private final String workingDirExpression;
    private final GradleDistribution gradleDistribution;
    private final String gradleUserHomeExpression;
    private final String javaHomeExpression;
    private final ImmutableList<String> jvmArgumentExpressions;
    private final ImmutableList<String> argumentExpressions;
    private final boolean visualizeTestProgress;

    /**
     * Creates a new instance.
     *
     * @param tasks the Gradle tasks to launch
     * @param workingDirExpression the expression resolving to the working directory from which to
     *            launch the Gradle tasks, never null
     * @param gradleDistribution the Gradle distribution to use
     * @param gradleUserHomeExpression the expression resolving to the Gradle user home to use, can
     *            be null
     * @param javaHomeExpression the expression resolving to the Java home to use, can be null
     * @param jvmArgumentExpressions the expressions resolving to the JVM arguments to apply
     * @param argumentExpressions the expressions resolving to the arguments to apply
     */
    private GradleRunConfigurationAttributes(List<String> tasks, String workingDirExpression, GradleDistribution gradleDistribution, String gradleUserHomeExpression,
            String javaHomeExpression, List<String> jvmArgumentExpressions, List<String> argumentExpressions, boolean visualizeTestProgress) {
        this.tasks = ImmutableList.copyOf(tasks);
        this.workingDirExpression = Preconditions.checkNotNull(workingDirExpression);
        this.gradleDistribution = Preconditions.checkNotNull(gradleDistribution);
        this.gradleUserHomeExpression = gradleUserHomeExpression;
        this.javaHomeExpression = javaHomeExpression;
        this.jvmArgumentExpressions = ImmutableList.copyOf(jvmArgumentExpressions);
        this.argumentExpressions = ImmutableList.copyOf(argumentExpressions);
        this.visualizeTestProgress = visualizeTestProgress;
    }

    public ImmutableList<String> getTasks() {
        return this.tasks;
    }

    public String getWorkingDirExpression() {
        return this.workingDirExpression;
    }

    public File getWorkingDir() {
        try {
            String location = ExpressionUtils.decode(this.workingDirExpression);
            return new File(location).getAbsoluteFile();
        } catch (CoreException e) {
            String message = String.format(CoreMessages.GradleRunConfigurationAttributes_ErrorMessage_CanNotResolveWorkingDirectoryExpression, this.workingDirExpression);
            CorePlugin.logger().error(message, e);
            throw new GradlePluginsRuntimeException(message);
        }
    }

    public GradleDistribution getGradleDistribution() {
        return this.gradleDistribution;
    }

    public String getGradleUserHomeExpression() {
        return this.gradleUserHomeExpression;
    }

    public File getGradleUserHome() {
        try {
            String location = ExpressionUtils.decode(this.gradleUserHomeExpression);
            return FileUtils.getAbsoluteFile(location).orNull();
        } catch (CoreException e) {
            String message = String.format(CoreMessages.GradleRunConfigurationAttributes_ErrorMessage_CanNotResolveHomeDirectoryExpression, this.gradleUserHomeExpression);
            CorePlugin.logger().error(message, e);
            throw new GradlePluginsRuntimeException(message);
        }
    }

    public String getJavaHomeExpression() {
        return this.javaHomeExpression;
    }

    public File getJavaHome() {
        try {
            String location = ExpressionUtils.decode(this.javaHomeExpression);
            return FileUtils.getAbsoluteFile(location).orNull();
        } catch (CoreException e) {
            String message = String.format(CoreMessages.GradleRunConfigurationAttributes_ErrorMessage_CanNotResolveJavaHomeDirectoryExpression, this.javaHomeExpression);
            CorePlugin.logger().error(message, e);
            throw new GradlePluginsRuntimeException(message);
        }
    }

    public ImmutableList<String> getJvmArgumentExpressions() {
        return this.jvmArgumentExpressions;
    }

    public ImmutableList<String> getJvmArguments() {
        return FluentIterable.from(this.jvmArgumentExpressions).transform(new Function<String, String>() {

            @Override
            public String apply(String input) {
                try {
                    return ExpressionUtils.decode(input);
                } catch (CoreException e) {
                    String message = String.format(CoreMessages.GradleRunConfigurationAttributes_ErrorMessage_CanNotResolveJVMArgumentExpression, input);
                    CorePlugin.logger().error(message, e);
                    throw new GradlePluginsRuntimeException(message);
                }
            }
        }).toList();
    }

    public ImmutableList<String> getArgumentExpressions() {
        return this.argumentExpressions;
    }

    public ImmutableList<String> getArguments() {
        return FluentIterable.from(this.argumentExpressions).transform(new Function<String, String>() {

            @Override
            public String apply(String input) {
                try {
                    return ExpressionUtils.decode(input);
                } catch (CoreException e) {
                    String message = String.format(CoreMessages.GradleRunConfigurationAttributes_ErrorMessage_CanNotResolveArgumentExpression, input);
                    CorePlugin.logger().error(message, e);
                    throw new GradlePluginsRuntimeException(message);
                }
            }
        }).toList();
    }

    public boolean isVisualizeTestProgress() {
        return this.visualizeTestProgress;
    }

    public boolean hasSameUniqueAttributes(ILaunchConfiguration launchConfiguration) {
        // reuse an existing run configuration if the working directory and the tasks are the same,
        // regardless of the other settings of the launch configuration
        try {
            return this.tasks.equals(launchConfiguration.getAttribute(TASKS, ImmutableList.<String> of()))
                    && this.workingDirExpression.equals(launchConfiguration.getAttribute(WORKING_DIR, "")); //$NON-NLS-1$
        } catch (CoreException e) {
            String message = String.format(CoreMessages.GradleRunConfigurationAttributes_ErrorMessage_CanNotReadLaunchConfig, launchConfiguration);
            CorePlugin.logger().error(message, e);
            throw new GradlePluginsRuntimeException(message, e);
        }
    }

    public void apply(ILaunchConfigurationWorkingCopy launchConfiguration) {
        applyTasks(this.tasks, launchConfiguration);
        applyWorkingDirExpression(this.workingDirExpression, launchConfiguration);
        applyGradleDistribution(this.gradleDistribution, launchConfiguration);
        applyGradleUserHomeExpression(this.gradleUserHomeExpression, launchConfiguration);
        applyJavaHomeExpression(this.javaHomeExpression, launchConfiguration);
        applyJvmArgumentExpressions(this.jvmArgumentExpressions, launchConfiguration);
        applyArgumentExpressions(this.argumentExpressions, launchConfiguration);
        applyVisualizeTestProgress(this.visualizeTestProgress, launchConfiguration);
    }

    public static void applyTasks(List<String> tasks, ILaunchConfigurationWorkingCopy launchConfiguration) {
        launchConfiguration.setAttribute(TASKS, tasks);
    }

    public static void applyWorkingDirExpression(String workingDirExpression, ILaunchConfigurationWorkingCopy launchConfiguration) {
        launchConfiguration.setAttribute(WORKING_DIR, Preconditions.checkNotNull(workingDirExpression));
    }

    public static void applyGradleDistribution(GradleDistribution gradleDistribution, ILaunchConfigurationWorkingCopy launchConfiguration) {
        launchConfiguration.setAttribute(GRADLE_DISTRIBUTION, Preconditions.checkNotNull(GradleDistributionSerializer.INSTANCE.serializeToString(gradleDistribution)));
    }

    public static void applyGradleUserHomeExpression(String gradleUserHomeExpression, ILaunchConfigurationWorkingCopy launchConfiguration) {
        launchConfiguration.setAttribute(GRADLE_USER_HOME, gradleUserHomeExpression);
    }

    public static void applyJavaHomeExpression(String javaHomeExpression, ILaunchConfigurationWorkingCopy launchConfiguration) {
        launchConfiguration.setAttribute(JAVA_HOME, javaHomeExpression);
    }

    public static void applyJvmArgumentExpressions(List<String> jvmArgumentsExpression, ILaunchConfigurationWorkingCopy launchConfiguration) {
        launchConfiguration.setAttribute(JVM_ARGUMENTS, jvmArgumentsExpression);
    }

    public static void applyArgumentExpressions(List<String> argumentsExpression, ILaunchConfigurationWorkingCopy launchConfiguration) {
        launchConfiguration.setAttribute(ARGUMENTS, argumentsExpression);
    }

    public static void applyVisualizeTestProgress(boolean visualizeTestProgress, ILaunchConfigurationWorkingCopy launchConfiguration) {
        launchConfiguration.setAttribute(VISUALIZE_TEST_PROGRESS, visualizeTestProgress);
    }

    public static GradleRunConfigurationAttributes with(List<String> tasks, String workingDirExpression, GradleDistribution gradleDistribution, String gradleUserHomeExpression,
            String javaHomeExpression, List<String> jvmArgumentExpressions, List<String> argumentExpressions, boolean visualizeTestProgress) {
        return new GradleRunConfigurationAttributes(tasks, workingDirExpression, gradleDistribution, gradleUserHomeExpression, javaHomeExpression, jvmArgumentExpressions,
                argumentExpressions, visualizeTestProgress);
    }

    @SuppressWarnings("unchecked")
    public static GradleRunConfigurationAttributes from(ILaunchConfiguration launchConfiguration) {
        List<String> tasks;
        try {
            tasks = launchConfiguration.getAttribute(TASKS, ImmutableList.<String> of());
        } catch (CoreException e) {
            String message = String.format(CoreMessages.GradleRunConfigurationAttributes_ErrorMessage_CanNotReadConfigAttribute, TASKS);
            CorePlugin.logger().error(message, e);
            throw new GradlePluginsRuntimeException(message);
        }

        String workingDirExpression;
        try {
            workingDirExpression = launchConfiguration.getAttribute(WORKING_DIR, ""); //$NON-NLS-1$
        } catch (CoreException e) {
            String message = String.format(CoreMessages.GradleRunConfigurationAttributes_ErrorMessage_CanNotReadConfigAttribute, WORKING_DIR);
            CorePlugin.logger().error(message, e);
            throw new GradlePluginsRuntimeException(message);
        }

        GradleDistribution gradleDistribution;
        try {
            String serialized = launchConfiguration.getAttribute(GRADLE_DISTRIBUTION, (String) null);
            gradleDistribution = serialized != null ? GradleDistributionSerializer.INSTANCE.deserializeFromString(serialized) : GradleDistribution.fromBuild();
        } catch (CoreException e) {
            String message = String.format(CoreMessages.GradleRunConfigurationAttributes_ErrorMessage_CanNotReadConfigAttribute, GRADLE_DISTRIBUTION);
            CorePlugin.logger().error(message, e);
            throw new GradlePluginsRuntimeException(message);
        }

        String gradleUserHomeExpression;
        try {
            gradleUserHomeExpression = launchConfiguration.getAttribute(GRADLE_USER_HOME, (String) null);
        } catch (CoreException e) {
            String message = String.format(CoreMessages.GradleRunConfigurationAttributes_ErrorMessage_CanNotReadConfigAttribute, GRADLE_USER_HOME);
            CorePlugin.logger().error(message, e);
            throw new GradlePluginsRuntimeException(message);
        }

        String javaHomeExpression;
        try {
            javaHomeExpression = launchConfiguration.getAttribute(JAVA_HOME, (String) null);
        } catch (CoreException e) {
            String message = String.format(CoreMessages.GradleRunConfigurationAttributes_ErrorMessage_CanNotReadConfigAttribute, JAVA_HOME);
            CorePlugin.logger().error(message, e);
            throw new GradlePluginsRuntimeException(message);
        }

        List<String> jvmArgumentExpressions;
        try {
            jvmArgumentExpressions = launchConfiguration.getAttribute(JVM_ARGUMENTS, ImmutableList.<String> of());
        } catch (CoreException e) {
            String message = String.format(CoreMessages.GradleRunConfigurationAttributes_ErrorMessage_CanNotReadConfigAttribute, JVM_ARGUMENTS);
            CorePlugin.logger().error(message, e);
            throw new GradlePluginsRuntimeException(message);
        }

        List<String> argumentExpressions;
        try {
            argumentExpressions = launchConfiguration.getAttribute(ARGUMENTS, ImmutableList.<String> of());
        } catch (CoreException e) {
            String message = String.format(CoreMessages.GradleRunConfigurationAttributes_ErrorMessage_CanNotReadConfigAttribute, ARGUMENTS);
            CorePlugin.logger().error(message, e);
            throw new GradlePluginsRuntimeException(message);
        }

        boolean visualizeTestProgress;
        try {
            visualizeTestProgress = launchConfiguration.getAttribute(VISUALIZE_TEST_PROGRESS, true);
        } catch (CoreException e) {
            String message = String.format(CoreMessages.GradleRunConfigurationAttributes_ErrorMessage_CanNotReadConfigAttribute, VISUALIZE_TEST_PROGRESS);
            CorePlugin.logger().error(message, e);
            throw new GradlePluginsRuntimeException(message);
        }

        return with(tasks, workingDirExpression, gradleDistribution, gradleUserHomeExpression, javaHomeExpression, jvmArgumentExpressions, argumentExpressions,
                visualizeTestProgress);
    }

}
