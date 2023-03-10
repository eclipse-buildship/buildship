/*******************************************************************************
 * Copyright (c) 2019 Gradle Inc. and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.core.internal.launch;

import java.io.File;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;

import org.gradle.tooling.TestLauncher;
import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.task.TaskOperationDescriptor;
import org.gradle.tooling.events.test.JvmTestOperationDescriptor;
import org.gradle.tooling.events.test.TestOperationDescriptor;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.eclipse.buildship.core.internal.configuration.BaseRunConfiguration;
import org.eclipse.buildship.core.internal.configuration.TestRunConfiguration;
import org.eclipse.buildship.core.internal.console.ProcessDescription;
import org.eclipse.buildship.core.internal.gradle.GradleProgressAttributes;
import org.eclipse.buildship.core.internal.i18n.CoreMessages;
import org.eclipse.buildship.core.internal.workspace.InternalGradleBuild;

/**
 * Executes tests through Gradle based on a given list of {@code TestOperationDescriptor} instances and a given set of {@code GradleRunConfigurationAttributes}.
 */
public final class RunGradleTestLaunchRequestJob extends BaseLaunchRequestJob<TestLauncher> {

    private final ImmutableList<TestOperationDescriptor> testDescriptors;
    private final TestRunConfiguration runConfiguration;

    public RunGradleTestLaunchRequestJob(List<TestOperationDescriptor> testDescriptors, TestRunConfiguration runConfig) {
        super("Launching Gradle tests");
        this.testDescriptors = ImmutableList.copyOf(testDescriptors);
        this.runConfiguration = Preconditions.checkNotNull(runConfig);
    }

    @Override
    protected BaseRunConfiguration getRunConfig() {
        return this.runConfiguration;
    }

    @Override
    protected ProcessDescription createProcessDescription() {
        String processName = createProcessName(this.runConfiguration.getProjectConfiguration().getProjectDir());
        return new TestLaunchProcessDescription(processName);
    }

    private String createProcessName(File workingDir) {
        return String.format("%s [Gradle Project] %s in %s (%s)", collectTestTaskNames(this.testDescriptors), Joiner.on(' ').join(collectSimpleDisplayNames(this.testDescriptors)),
                workingDir.getAbsolutePath(), DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(new Date()));
    }

    private String collectTestTaskNames(List<TestOperationDescriptor> testDescriptors) {
        ImmutableList.Builder<String> testTaskNames = ImmutableList.builder();
        for (TestOperationDescriptor testDescriptor : testDescriptors) {
            Optional<TaskOperationDescriptor> taskDescriptor = findParentTestTask(testDescriptor);
            testTaskNames.add(taskDescriptor.isPresent() ? taskDescriptor.get().getTaskPath() : "Test");
        }
        return Joiner.on(' ').join(ImmutableSet.copyOf(testTaskNames.build()));
    }

    private Optional<TaskOperationDescriptor> findParentTestTask(OperationDescriptor testDescriptor) {
        OperationDescriptor parent = testDescriptor.getParent();
        if (parent instanceof TaskOperationDescriptor) {
            return Optional.of((TaskOperationDescriptor) parent);
        } else if (parent != null) {
            return findParentTestTask(parent);
        } else {
            return Optional.absent();
        }
    }

    @Override
    protected TestLauncher createLaunch(InternalGradleBuild gradleBuild, GradleProgressAttributes progressAttributes, ProcessDescription processDescription) {
        TestLauncher launcher = gradleBuild.newTestLauncher(this.runConfiguration, progressAttributes);
        launcher.withTests(RunGradleTestLaunchRequestJob.this.testDescriptors);
        return launcher;
    }

    @Override
    protected void executeLaunch(TestLauncher launcher) {
        launcher.run();
    }

    @Override
    protected void writeExtraConfigInfo(GradleProgressAttributes progressAttributes) {
        progressAttributes.writeConfig(String.format("%s: %s", CoreMessages.RunConfiguration_Label_Tests, Joiner.on(' ').join(collectQualifiedDisplayNames(this.testDescriptors))));
    }

    private static List<String> collectQualifiedDisplayNames(List<TestOperationDescriptor> testDescriptors) {
        return FluentIterable.from(testDescriptors).transform(new Function<TestOperationDescriptor, String>() {

            @Override
            public String apply(TestOperationDescriptor descriptor) {
                if (descriptor instanceof JvmTestOperationDescriptor) {
                    JvmTestOperationDescriptor jvmTestDescriptor = (JvmTestOperationDescriptor) descriptor;
                    String className = jvmTestDescriptor.getClassName();
                    String methodName = jvmTestDescriptor.getMethodName();
                    return methodName != null ? className + "#" + methodName : className;
                } else {
                    return descriptor.getDisplayName();
                }
            }
        }).toList();
    }

    private static List<String> collectSimpleDisplayNames(List<TestOperationDescriptor>  testDescriptors) {
        return FluentIterable.from(testDescriptors).transform(new Function<TestOperationDescriptor, String>() {

            @Override
            public String apply(TestOperationDescriptor descriptor) {
                if (descriptor instanceof JvmTestOperationDescriptor) {
                    JvmTestOperationDescriptor jvmTestDescriptor = (JvmTestOperationDescriptor) descriptor;
                    String className = jvmTestDescriptor.getClassName();
                    String methodName = jvmTestDescriptor.getMethodName();
                    int index = className.lastIndexOf('.');
                    if (index >= 0 && className.length() > index + 1) {
                        className = className.substring(index + 1);
                    }
                    return methodName != null ? className + "#" + methodName : className;
                } else {
                    return descriptor.getDisplayName();
                }
            }
        }).toList();
    }

    /**
     * Implementation of {@code ProcessDescription}.
     */
    private final class TestLaunchProcessDescription extends BaseProcessDescription {

        public TestLaunchProcessDescription(String processName) {
            super(processName, RunGradleTestLaunchRequestJob.this, RunGradleTestLaunchRequestJob.this.runConfiguration);
        }

        @Override
        public boolean isRerunnable() {
            return true;
        }

        @Override
        public void rerun() {
            RunGradleTestLaunchRequestJob job = new RunGradleTestLaunchRequestJob(
                    RunGradleTestLaunchRequestJob.this.testDescriptors,
                    RunGradleTestLaunchRequestJob.this.runConfiguration
            );
            job.schedule();
        }

    }
}
