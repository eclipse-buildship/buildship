/*
 * Copyright (c) 2016 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.eclipse.buildship.core.workspace.internal;

import java.util.Set;

import org.gradle.tooling.CancellationTokenSource;

import com.google.common.base.Preconditions;

import com.gradleware.tooling.toolingmodel.OmniEclipseProject;
import com.gradleware.tooling.toolingmodel.repository.FetchStrategy;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;

import org.eclipse.buildship.core.configuration.BuildConfiguration;
import org.eclipse.buildship.core.workspace.GradleBuild;
import org.eclipse.buildship.core.workspace.ModelProvider;
import org.eclipse.buildship.core.workspace.NewProjectHandler;

/**
 * Synchronizes each of the given Gradle builds with the workspace.
 */
public final class SynchronizeGradleBuildsOperation {

    private final GradleBuild build;
    private final NewProjectHandler newProjectHandler;

    private SynchronizeGradleBuildsOperation(GradleBuild build, NewProjectHandler newProjectHandler) {
        this.build = Preconditions.checkNotNull(build);
        this.newProjectHandler = Preconditions.checkNotNull(newProjectHandler);
    }

    protected void run(CancellationTokenSource tokenSource, IProgressMonitor monitor) throws CoreException {
        SubMonitor progress = SubMonitor.convert(monitor, 5);
        BuildConfiguration buildConfig = this.build.getBuildConfig();
        progress.setTaskName((String.format("Synchronizing Gradle build at %s with workspace", buildConfig.getRootProjectDirectory())));
        new SynchronizeBuildConfigurationOperation(buildConfig).run(progress.newChild(1), tokenSource.token());
        Set<OmniEclipseProject> allProjects = fetchEclipseProjects(this.build, tokenSource, progress.newChild(1));
        new ValidateProjectLocationOperation(allProjects).run(progress.newChild(1));
        new RunOnImportTasksOperation(allProjects, buildConfig).run(progress.newChild(1), tokenSource.token());
        new SynchronizeGradleBuildOperation(allProjects, buildConfig, SynchronizeGradleBuildsOperation.this.newProjectHandler).run(progress.newChild(1));
    }

    private Set<OmniEclipseProject> fetchEclipseProjects(GradleBuild build, CancellationTokenSource tokenSource, SubMonitor progress) {
        progress.setTaskName("Loading Gradle project models");
        ModelProvider modelProvider = build.getModelProvider();
        return modelProvider.fetchEclipseGradleProjects(FetchStrategy.FORCE_RELOAD, tokenSource, progress);
    }

    public static SynchronizeGradleBuildsOperation forSingleGradleBuild(GradleBuild build, NewProjectHandler newProjectHandler) {
        return new SynchronizeGradleBuildsOperation(build, newProjectHandler);
    }
}
