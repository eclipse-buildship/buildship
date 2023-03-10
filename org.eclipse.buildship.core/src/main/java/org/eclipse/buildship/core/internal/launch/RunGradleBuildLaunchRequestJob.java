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

import org.gradle.tooling.BuildLauncher;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;

import org.eclipse.buildship.core.internal.CorePlugin;
import org.eclipse.buildship.core.internal.configuration.BaseRunConfiguration;
import org.eclipse.buildship.core.internal.configuration.RunConfiguration;
import org.eclipse.buildship.core.internal.console.ProcessDescription;
import org.eclipse.buildship.core.internal.gradle.GradleProgressAttributes;
import org.eclipse.buildship.core.internal.i18n.CoreMessages;
import org.eclipse.buildship.core.internal.util.collections.CollectionsUtils;
import org.eclipse.buildship.core.internal.workspace.InternalGradleBuild;

/**
 * Executes Gradle tasks based on a given {@code ILaunch} and {@code ILaunchConfiguration} instance.
 */
public final class RunGradleBuildLaunchRequestJob extends BaseLaunchRequestJob<BuildLauncher> {

    private final ILaunch launch;
    private final RunConfiguration runConfig;

    public RunGradleBuildLaunchRequestJob(ILaunch launch) {
        super("Launching Gradle tasks");
        this.launch = Preconditions.checkNotNull(launch);
        this.runConfig = CorePlugin.configurationManager().loadRunConfiguration(launch.getLaunchConfiguration());
    }

    @Override
    protected ProcessDescription createProcessDescription() {
        String processName = createProcessName(this.runConfig.getTasks(), this.runConfig.getProjectConfiguration().getProjectDir(), this.launch.getLaunchConfiguration().getName());
        return new BuildLaunchProcessDescription(processName);
    }

    private String createProcessName(List<String> tasks, File workingDir, String launchConfigurationName) {
        return String.format("%s [Gradle Project] %s in %s (%s)", launchConfigurationName, Joiner.on(' ').join(tasks), workingDir.getAbsolutePath(), DateFormat
                .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(new Date()));
    }

    @Override
    protected BuildLauncher createLaunch(InternalGradleBuild gradleBuild, GradleProgressAttributes progressAttributes, ProcessDescription processDescription) {
        BuildLauncher launcher = gradleBuild.newBuildLauncher(this.runConfig, progressAttributes);
        launcher.forTasks(RunGradleBuildLaunchRequestJob.this.runConfig.getTasks().toArray(new String[0]));
        return launcher;
    }

    @Override
    protected void executeLaunch(BuildLauncher launcher) {
        launcher.run();
    }

    @Override
    protected void writeExtraConfigInfo(GradleProgressAttributes progressAttributes) {
        String taskNames = Strings.emptyToNull(CollectionsUtils.joinWithSpace(this.runConfig.getTasks()));
        taskNames = taskNames != null ? taskNames : CoreMessages.RunConfiguration_Value_RunDefaultTasks;
        progressAttributes.writeConfig(String.format("%s: %s%n", CoreMessages.RunConfiguration_Label_GradleTasks, taskNames));
    }

    @Override
    protected BaseRunConfiguration getRunConfig() {
       return this.runConfig;
    }

    /**
     * Implementation of {@code ProcessDescription}.
     */
    private final class BuildLaunchProcessDescription extends BaseProcessDescription {

        public BuildLaunchProcessDescription(String processName) {
            super(processName, RunGradleBuildLaunchRequestJob.this, RunGradleBuildLaunchRequestJob.this.runConfig);
        }

        @Override
        public boolean isRerunnable() {
            ILaunchConfiguration[] launchConfigurations;
            try {
                launchConfigurations = DebugPlugin.getDefault().getLaunchManager().getLaunchConfigurations();
            } catch (CoreException e) {
                return false;
            }

            ILaunchConfiguration targetLaunchConfiguration = RunGradleBuildLaunchRequestJob.this.launch.getLaunchConfiguration();
            for (ILaunchConfiguration launchConfiguration : launchConfigurations) {
                if (launchConfiguration.equals(targetLaunchConfiguration)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void rerun() {
            ILaunch launch = RunGradleBuildLaunchRequestJob.this.launch;
            CorePlugin.gradleLaunchConfigurationManager().launch(launch.getLaunchConfiguration(), launch.getLaunchMode());
        }

    }

}
