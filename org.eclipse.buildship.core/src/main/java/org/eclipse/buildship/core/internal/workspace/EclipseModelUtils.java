/*******************************************************************************
 * Copyright (c) 2023 Gradle Inc. and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.core.internal.workspace;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.buildship.core.internal.CorePlugin;
import org.eclipse.buildship.core.internal.UnsupportedConfigurationException;
import org.eclipse.buildship.core.internal.util.gradle.GradleVersion;
import org.eclipse.buildship.core.internal.util.gradle.IdeFriendlyClassLoading;
import org.eclipse.buildship.core.internal.util.gradle.SimpleIntermediateResultHandler;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildActionFailureException;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.eclipse.EclipseProject;
import org.gradle.tooling.model.eclipse.EclipseRuntime;
import org.gradle.tooling.model.eclipse.EclipseWorkspaceProject;
import org.gradle.tooling.model.eclipse.RunClosedProjectBuildDependencies;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public final class EclipseModelUtils {

    private static final String COULD_NOT_CREATE_TASK = "Could not create task ";
    private static final String EXCEPTION_DUPLICATE_ROOT_ELEMENT_TEXT = "Duplicate root element ";

    private EclipseModelUtils() {
    }

    public static Map<String, EclipseProject> queryModels(ProjectConnection connection) {
        BuildEnvironment buildEnvironment = connection.getModel(BuildEnvironment.class);
        GradleVersion gradleVersion = GradleVersion.version(buildEnvironment.getGradle().getGradleVersion());
        if (gradleVersion.supportsSendingReservedProjects()) {
            return queryCompositeModelWithRuntimInfo(connection, gradleVersion);
        } else if (gradleVersion.supportsCompositeBuilds()) {
            return queryCompositeModel(EclipseProject.class, connection);
        } else {
            return ImmutableMap.of(":", queryModel(EclipseProject.class, connection));
        }
    }

    public static Map<String, EclipseProject> runTasksAndQueryModels(ProjectConnection connection) {
        BuildEnvironment buildEnvironment = connection.getModel(BuildEnvironment.class);
        GradleVersion gradleVersion = GradleVersion.version(buildEnvironment.getGradle().getGradleVersion());
        if (gradleVersion.supportsSendingReservedProjects()) {
            return runTasksAndQueryCompositeModelWithRuntimInfo(connection, gradleVersion);
        } else if (gradleVersion.supportsSyncTasksInEclipsePluginConfig()) {
            return runTasksAndQueryCompositeModel(connection, gradleVersion);
        } else if (gradleVersion.supportsCompositeBuilds()) {
            return queryCompositeModel(EclipseProject.class, connection);
        } else {
            return ImmutableMap.of(":", queryModel(EclipseProject.class, connection));
        }
    }

    public static EclipseRuntimeConfigurer buildEclipseRuntimeConfigurer() {
        ImmutableList<IProject> allWorkspaceProjects = CorePlugin.workspaceOperations().getAllProjects();
        List<EclipseWorkspaceProject> projects = allWorkspaceProjects.stream().map(p -> new DefaultEclipseWorkspaceProject(p.getName(), p.getLocation().toFile(), p.isOpen()))
                .collect(Collectors.toList());
        return new EclipseRuntimeConfigurer(new DefaultEclipseWorkspace(ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile(), projects));
    }


    private static Map<String, EclipseProject> runTasksAndQueryCompositeModelWithRuntimInfo(ProjectConnection connection, GradleVersion gradleVersion) {
        EclipseRuntimeConfigurer buildEclipseRuntimeConfigurer = buildEclipseRuntimeConfigurer();
        try {
            BuildAction<Void> runSyncTasksAction = IdeFriendlyClassLoading.loadClass(TellGradleToRunSynchronizationTasks.class);
            if (gradleVersion.supportsClosedProjectDependencySubstitution()) {
                // use a composite query to run substitute tasks in included builds too
                BuildAction<?> runClosedProjectTasksAction = new CompositeModelQuery<>(RunClosedProjectBuildDependencies.class, EclipseRuntime.class, buildEclipseRuntimeConfigurer);
                BuildActionSequence projectsLoadedAction = new BuildActionSequence(runSyncTasksAction, runClosedProjectTasksAction);
                return runPhasedModelQuery(connection, gradleVersion, projectsLoadedAction, IdeFriendlyClassLoading
                        .loadCompositeModelQuery(EclipseProject.class, EclipseRuntime.class,buildEclipseRuntimeConfigurer));
            }
            return runPhasedModelQuery(connection, gradleVersion, runSyncTasksAction, IdeFriendlyClassLoading
                    .loadCompositeModelQuery(EclipseProject.class, EclipseRuntime.class, buildEclipseRuntimeConfigurer));
        } catch (BuildActionFailureException e) {
            // For gradle >= 5.5 project name deduplication happens in gradle. In case gradle can't deduplicate then create an UnsupportedConfigurationException
            // to match the behaviour with previous gradle versions.
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException && cause.getMessage().startsWith(EXCEPTION_DUPLICATE_ROOT_ELEMENT_TEXT)) {
                String projectName = cause.getMessage().substring(EXCEPTION_DUPLICATE_ROOT_ELEMENT_TEXT.length());
                String message = String.format("A project with the name %s already exists.", projectName);
                throw new UnsupportedConfigurationException(message, e);
            }
            // https://github.com/eclipse-buildship/buildship/issues/1355
            if (cause.getMessage() != null && cause.getMessage().startsWith(COULD_NOT_CREATE_TASK)) {
                GradleProject model = connection.model(GradleProject.class).get();
                Set<String> tasks = new HashSet<>();
                collectTasks(model, tasks);
                List<String> toExclude = Arrays.asList(
                    "test", "check", "testClasses", "javadoc", "distZip",
                    "distTar", "shadowJar", "sourcesJar", "assembleDist",
                    "installDist"
                );
                List<String> arguments = new ArrayList<>();
                for (String task : toExclude) {
                    if (tasks.contains(task)) {
                        arguments.add("-x");
                        arguments.add(task);
                    }
                }
                connection.newBuild()
                    .forTasks("build")
                    .withArguments(arguments)
                    .run();
                return ImmutableMap.of(":", queryModel(EclipseProject.class, connection));
            }
            throw e;
        }
    }

    private static void collectTasks(GradleProject project, Set<String> tasks) {
        project.getTasks().forEach(t -> tasks.add(t.getName()));
        for (GradleProject child : project.getChildren()) {
            collectTasks(child, tasks);
        }
    }

    private static Map<String, EclipseProject> runTasksAndQueryCompositeModel(ProjectConnection connection, GradleVersion gradleVersion) {
        return runPhasedModelQuery(connection, gradleVersion, IdeFriendlyClassLoading.loadClass(TellGradleToRunSynchronizationTasks.class), IdeFriendlyClassLoading.loadCompositeModelQuery(EclipseProject.class));
    }

    private static Map<String, EclipseProject> runPhasedModelQuery(ProjectConnection connection, GradleVersion gradleVersion,
            BuildAction<Void> projectsLoadedAction, BuildAction<Map<String, EclipseProject>> query) {
        SimpleIntermediateResultHandler<Map<String, EclipseProject>> resultHandler = new SimpleIntermediateResultHandler<>();
        connection.action().projectsLoaded(projectsLoadedAction, new SimpleIntermediateResultHandler<Void>()).buildFinished(query, resultHandler).build().forTasks().run();
        return resultHandler.getValue();
    }

    private static Map<String, EclipseProject> queryCompositeModelWithRuntimInfo(ProjectConnection connection, GradleVersion gradleVersion) {
        BuildAction<Map<String, EclipseProject>> query = IdeFriendlyClassLoading.loadCompositeModelQuery(EclipseProject.class, EclipseRuntime.class, buildEclipseRuntimeConfigurer());
        return connection.action(query).run();
    }

    private static <T> Map<String, T> queryCompositeModel(Class<T> model, ProjectConnection connection) {
        BuildAction<Map<String, T>> query = IdeFriendlyClassLoading.loadCompositeModelQuery(model);
        return connection.action(query).run();
    }

    private static <T> T queryModel(Class<T> model, ProjectConnection connection) {
        return connection.getModel(model);
    }
}
