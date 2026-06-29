/*******************************************************************************
 * Copyright (c) 2023 Gradle Inc. and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.core.internal;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.CancellationTokenSource;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.TestLauncher;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.eclipse.EclipseProject;

import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableSet;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.ISchedulingRule;

import org.eclipse.buildship.core.SynchronizationResult;
import org.eclipse.buildship.core.internal.configuration.GradleArguments;
import org.eclipse.buildship.core.internal.configuration.RunConfiguration;
import org.eclipse.buildship.core.internal.configuration.TestRunConfiguration;
import org.eclipse.buildship.core.internal.gradle.GradleProgressAttributes;
import org.eclipse.buildship.core.internal.marker.GradleErrorMarker;
import org.eclipse.buildship.core.internal.marker.GradleMarkerManager;
import org.eclipse.buildship.core.internal.operation.BaseToolingApiOperation;
import org.eclipse.buildship.core.internal.operation.ToolingApiStatus;
import org.eclipse.buildship.core.internal.util.gradle.HierarchicalElementUtils;
import org.eclipse.buildship.core.internal.util.gradle.IdeAttachedProjectConnection;
import org.eclipse.buildship.core.internal.workspace.ConnectionAwareLauncherProxy;
import org.eclipse.buildship.core.internal.workspace.DefaultModelProvider;
import org.eclipse.buildship.core.internal.workspace.FetchStrategy;
import org.eclipse.buildship.core.internal.workspace.ImportRootProjectOperation;
import org.eclipse.buildship.core.internal.workspace.InternalGradleBuild;
import org.eclipse.buildship.core.internal.workspace.ModelProvider;
import org.eclipse.buildship.core.internal.workspace.NewProjectHandler;
import org.eclipse.buildship.core.internal.workspace.ProjectConfigurators;
import org.eclipse.buildship.core.internal.workspace.RunOnImportTasksOperation;
import org.eclipse.buildship.core.internal.workspace.SynchronizationProblem;
import org.eclipse.buildship.core.internal.workspace.SynchronizeGradleBuildOperation;
import org.eclipse.buildship.core.internal.workspace.ValidateProjectLocationOperation;

public final class DefaultGradleBuild implements InternalGradleBuild {
    private static Map<File, SynchronizeOperation> syncOperations = new ConcurrentHashMap<>();

    // TODO (donat) Now, we have two caches: one for the project configurators and that lives within
    // a synchronization (projectConnectionCache field) and one that lives forever (modelProvider).
    // We should revisit this at some point and unify them.
    private final ModelProvider modelProvider;
    private final Cache<Object, Object> projectConnectionCache;
    private final org.eclipse.buildship.core.internal.configuration.BuildConfiguration buildConfig;

    public DefaultGradleBuild(org.eclipse.buildship.core.internal.configuration.BuildConfiguration buildConfiguration) {
        this.buildConfig = buildConfiguration;
        this.modelProvider = new DefaultModelProvider(this);
        this.projectConnectionCache = CacheBuilder.newBuilder().build();
    }

    @Override
    public SynchronizationResult synchronize(IProgressMonitor monitor) {
        return synchronize(NewProjectHandler.IMPORT_AND_MERGE, GradleConnector.newCancellationTokenSource(), monitor);
    }

    public SynchronizationResult synchronize(NewProjectHandler newProjectHandler, CancellationTokenSource tokenSource, IProgressMonitor monitor) {
        IStatus toolingApiCompatibilityStatus = CompatibilityChecker.validateToolingApiCompatibility(this, this.buildConfig, monitor);
        if (!toolingApiCompatibilityStatus.isOK()) {
            return DefaultSynchronizationResult.from(toolingApiCompatibilityStatus);
        }

        monitor = monitor != null ? monitor : new NullProgressMonitor();

        SynchronizeOperation operation = new SynchronizeOperation(this, newProjectHandler);
        SynchronizeOperation runningOperation = syncOperations.putIfAbsent(getBuildConfig().getRootProjectDirectory(), operation);

        if (runningOperation != null && (newProjectHandler == NewProjectHandler.NO_OP || Objects.equals(newProjectHandler, runningOperation.newProjectHandler))) {
            return DefaultSynchronizationResult.success();
        }

        try {
            return operation.run(tokenSource, monitor);
        } finally {
            syncOperations.remove(this.getBuildConfig().getRootProjectDirectory());
        }
    }

    public boolean isSynchronizing() {
        return syncOperations.containsKey(this.getBuildConfig().getRootProjectDirectory());
    }

    @Override
    public <T> T withConnection(Function<ProjectConnection, ? extends T> action, IProgressMonitor monitor) throws Exception {
        return withConnection(action, GradleConnector.newCancellationTokenSource(), monitor);
    }

    @Override
    public <T> T withConnection(Function<ProjectConnection, ? extends T> action, CancellationTokenSource tokenSource, IProgressMonitor monitor) throws Exception {
        Preconditions.checkNotNull(action);
        monitor = monitor != null ? monitor : new NullProgressMonitor();

        GradleConnectionOperation<T> operation = new GradleConnectionOperation<>(action);
        try {
            CorePlugin.operationManager().run(operation, tokenSource, monitor);
            return operation.result;
        } catch (CoreException e) {
            if (e.getStatus().getException() instanceof Exception) {
                throw (Exception) e.getStatus().getException();
            } else {
                throw e;
            }
        }
    }

    @Override
    public ModelProvider getModelProvider() {
        return this.modelProvider;
    }

    @Override
    public BuildLauncher newBuildLauncher(RunConfiguration runConfiguration, GradleProgressAttributes progressAttributes) {
        return ConnectionAwareLauncherProxy.newBuildLauncher(runConfiguration.toGradleArguments(), progressAttributes);
    }

    @Override
    public TestLauncher newTestLauncher(TestRunConfiguration testRunConfiguration, GradleProgressAttributes progressAttributes) {
        return ConnectionAwareLauncherProxy.newTestLauncher(testRunConfiguration.toGradleArguments(), progressAttributes);
    }

    @Override
    public org.eclipse.buildship.core.internal.configuration.BuildConfiguration getBuildConfig() {
        return this.buildConfig;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.buildConfig);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        DefaultGradleBuild other = (DefaultGradleBuild) obj;
        return Objects.equals(this.buildConfig, other.buildConfig);
    }

    /**
     * Executes the synchronization on the target Gradle build.
     */
    private static class SynchronizeOperation extends BaseToolingApiOperation {

        private final DefaultGradleBuild gradleBuild;
        private final NewProjectHandler newProjectHandler;
        private List<SynchronizationProblem> failures;

        public SynchronizeOperation(DefaultGradleBuild gradleBuild, NewProjectHandler newProjectHandler) {
            super("Synchronize project " + gradleBuild.getBuildConfig().getRootProjectDirectory().getName());
            this.gradleBuild = gradleBuild;
            this.newProjectHandler = newProjectHandler;
        }

        SynchronizationResult run(CancellationTokenSource tokenSource, IProgressMonitor monitor) {
            GradleMarkerManager.clear(this.gradleBuild);
            DefaultSynchronizationResult result;
            try {
                CorePlugin.operationManager().run(this, tokenSource, monitor);
                for (SynchronizationProblem f : this.failures) {
                    if (f.getSeverity() == IStatus.ERROR) {
                        GradleErrorMarker.createError(f.getResource(), this.gradleBuild, f.getMessage(), f.getException());
                    } else if (f.getSeverity() == IStatus.WARNING) {
                        GradleErrorMarker.createWarning(f.getResource(), this.gradleBuild, f.getMessage(), f.getException());
                    }
                }
                result = DefaultSynchronizationResult.from(getFailures());
            } catch (CoreException e) {
                ToolingApiStatus status = ToolingApiStatus.from("Project synchronization", e);
                if (status.severityMatches(IStatus.WARNING | IStatus.ERROR)) {
                    GradleMarkerManager.addError(this.gradleBuild, status);
                }
                result = DefaultSynchronizationResult.from(e.getStatus());
            }

            if (result.status.matches(IStatus.WARNING | IStatus.ERROR)) {
                CorePlugin.getInstance().getLog().log(result.status);
            }

            return result;
        }

        public List<SynchronizationProblem> getFailures() {
            return this.failures;
        }

        @Override
        public void runInToolingApi(CancellationTokenSource tokenSource, IProgressMonitor monitor) throws Exception {
            ToolingApiSystemProperties.withSyncActive(() -> {
                try {
                    SubMonitor progress = SubMonitor.convert(monitor, 6);
                    progress.setTaskName((String.format("Synchronizing Gradle build at %s with workspace", this.gradleBuild.getBuildConfig().getRootProjectDirectory())));
                    new ImportRootProjectOperation(this.gradleBuild.getBuildConfig(), this.newProjectHandler).run(progress.newChild(1));
                    // Force caching the result
                    // Note, that this is a TAPI client-side operation and does not trigger configuration
                    this.gradleBuild.modelProvider.fetchModel(BuildEnvironment.class, FetchStrategy.FORCE_RELOAD, tokenSource, progress.newChild(1));
                    Set<EclipseProject> allProjects = collectAll(this.gradleBuild.modelProvider.fetchEclipseProjectAndRunSyncTasks(tokenSource, progress.newChild(1)));
                    new ValidateProjectLocationOperation(allProjects).run(progress.newChild(1));
                    new RunOnImportTasksOperation(allProjects, this.gradleBuild.getBuildConfig()).run(progress.newChild(1), tokenSource);
                    this.failures = new SynchronizeGradleBuildOperation(allProjects, this.gradleBuild, this.newProjectHandler,
                            ProjectConfigurators.create(this.gradleBuild, CorePlugin.extensionManager().loadConfigurators())).run(progress.newChild(1));
                } finally {
                    this.gradleBuild.projectConnectionCache.invalidateAll();
                }
            });
        }

        @Override
        public ISchedulingRule getRule() {
            return ResourcesPlugin.getWorkspace().getRoot();
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.gradleBuild, this.newProjectHandler);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            SynchronizeOperation other = (SynchronizeOperation) obj;
            return Objects.equals(this.gradleBuild, other.gradleBuild) && Objects.equals(this.newProjectHandler, other.newProjectHandler);
        }
    }

    private static Set<EclipseProject> collectAll(Map<String, EclipseProject> models) {
        ImmutableSet.Builder<EclipseProject> result = ImmutableSet.builder();
        for (Entry<String, EclipseProject> model : models.entrySet()) {
            result.addAll(HierarchicalElementUtils.getAll(model.getValue()));
        }
        return result.build();
    }

    private static class DefaultSynchronizationResult implements SynchronizationResult {

        private final IStatus status;

        private DefaultSynchronizationResult(IStatus status) {
            this.status = status;
        }

        @Override
        public IStatus getStatus() {
            return this.status;
        }

        public static DefaultSynchronizationResult success() {
            return new DefaultSynchronizationResult(Status.OK_STATUS);
        }

        public static DefaultSynchronizationResult from(List<SynchronizationProblem> failures) {
            if (failures.isEmpty()) {
                return success();
            } else if (failures.size() == 1) {
                return from(statusFor(failures.get(0)));
            } else {
                boolean internalFailure = failures.stream().filter(f -> CorePlugin.PLUGIN_ID.equals(f.getPluginId())).findFirst().isPresent();
                String pluginId = internalFailure ? CorePlugin.PLUGIN_ID : failures.get(0).getPluginId();
                MultiStatus status = new MultiStatus(pluginId, 0, "Gradle synchronization failed with multiple errors", null);
                failures.forEach(f -> status.addAll(statusFor(f)));
                return from(status);
            }
        }

        public static DefaultSynchronizationResult from(IStatus status) {
            return new DefaultSynchronizationResult(status);
        }

        private static IStatus statusFor(SynchronizationProblem failure) {
            return new Status(failure.getSeverity(), failure.getPluginId(), failure.getMessage(), failure.getException());
        }
    }

    private class GradleConnectionOperation<T> extends BaseToolingApiOperation {

        private final Function<ProjectConnection, ? extends T> action;
        private T result;

        public GradleConnectionOperation(Function<ProjectConnection, ? extends T> action) {
            super("Connecting to Gradle");
            this.action = action;
        }

        @Override
        public void runInToolingApi(CancellationTokenSource tokenSource, IProgressMonitor monitor) throws Exception {
            // TODO (donat) use AutoCloseable once we update to Tooling API 5.0
            ProjectConnection connection = IdeAttachedProjectConnection.newInstance(tokenSource, getGradleArguments(), DefaultGradleBuild.this, monitor);
            if (isSynchronizing()) {
                connection = new CachingProjectConnection(connection, DefaultGradleBuild.this.projectConnectionCache);
            }

            try {
                this.result = this.action.apply(connection);
            } finally {
                connection.close();
            }
        }

        private GradleArguments getGradleArguments() {
            return DefaultGradleBuild.this.buildConfig.toGradleArguments();
        }

        @Override
        public ISchedulingRule getRule() {
            return null;
        }
    }
}
