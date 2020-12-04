/*******************************************************************************
 * Copyright (c) 2019 Gradle Inc.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.ui.internal.view.task;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import org.gradle.tooling.CancellationTokenSource;
import org.gradle.tooling.model.eclipse.EclipseProject;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.ui.PlatformUI;

import org.eclipse.buildship.core.internal.CorePlugin;
import org.eclipse.buildship.core.internal.configuration.GradleProjectNature;
import org.eclipse.buildship.core.internal.operation.ToolingApiJob;
import org.eclipse.buildship.core.internal.operation.ToolingApiJobResultHandler;
import org.eclipse.buildship.core.internal.operation.ToolingApiStatus;
import org.eclipse.buildship.core.internal.workspace.FetchStrategy;
import org.eclipse.buildship.core.internal.workspace.InternalGradleBuild;

/**
 * Loads the tasks for all projects into the cache and refreshes the task view afterwards.
 */
final class ReloadTaskViewJob extends ToolingApiJob<TaskViewContent> {

    private final TaskView taskView;
    private final FetchStrategy modelFetchStrategy;

    public ReloadTaskViewJob(TaskView taskView, FetchStrategy modelFetchStrategy) {
        super("Loading tasks of all Gradle projects");
        this.taskView = Preconditions.checkNotNull(taskView);
        this.modelFetchStrategy = Preconditions.checkNotNull(modelFetchStrategy);
        setResultHandler(new ResultHandler());
        setRule(ResourcesPlugin.getWorkspace().getRoot());
    }

    @Override
    public TaskViewContent runInToolingApi(CancellationTokenSource tokenSource, IProgressMonitor monitor) throws Exception {
        return loadContent(tokenSource, monitor);
    }

    private TaskViewContent loadContent(CancellationTokenSource tokenSource, IProgressMonitor monitor) {
        Map<File, Map<String, EclipseProject>> allModels = new LinkedHashMap<>();
        for (InternalGradleBuild gradleBuild : CorePlugin.internalGradleWorkspace().getGradleBuilds()) {
            Map<String, EclipseProject> models = gradleBuild.getModelProvider().fetchModels(EclipseProject.class, this.modelFetchStrategy, tokenSource, monitor);
            allModels.put(gradleBuild.getBuildConfig().getRootProjectDirectory(), models);

        }
        return TaskViewContent.from(allModels, allGradleWorkspaceProjects());
    }

    private Map<String, IProject> allGradleWorkspaceProjects() {
        Map<String, IProject> result = Maps.newLinkedHashMap();
        for (IProject project : CorePlugin.workspaceOperations().getAllProjects()) {
            if (GradleProjectNature.isPresentOn(project)) {
                result.put(project.getName(), project);
            }
        }
        return result;
    }

    private void refreshTaskView(final TaskViewContent content) {
        PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable() {

            @Override
            public void run() {
                ReloadTaskViewJob.this.taskView.setContent(content);
            }
        });
    }

    @Override
    public boolean shouldSchedule() {
        Job[] jobs = Job.getJobManager().find(CorePlugin.GRADLE_JOB_FAMILY);
        for (Job job : jobs) {
            if (job instanceof ReloadTaskViewJob) {
                return false;
            }
        }
        return true;
    }

    /**
     * Custom result handler to present the results in the view.
     */
    private class ResultHandler implements ToolingApiJobResultHandler<TaskViewContent> {

        @Override
        public void onSuccess(TaskViewContent content) {
            refreshTaskView(content);
        }

        @Override
        public void onFailure(ToolingApiStatus status) {
            CorePlugin.getInstance().getLog().log(status);
        }
    }
}
