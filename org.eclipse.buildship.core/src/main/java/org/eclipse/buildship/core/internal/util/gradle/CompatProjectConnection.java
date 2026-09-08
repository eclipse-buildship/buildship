/*******************************************************************************
 * Copyright (c) 2023 Gradle Inc. and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.core.internal.util.gradle;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildActionExecuter;
import org.gradle.tooling.BuildActionExecuter.Builder;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.CancellationToken;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.IntermediateResultHandler;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ProgressListener;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.StreamedValueListener;
import org.gradle.tooling.TestLauncher;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.model.eclipse.EclipseProject;

import com.google.common.collect.Maps;

/**
 * Injects {@link CompatEclipseProject} into all model queries requesting the {@link EclipseProject}
 * model.
 *
 * @author Donat Csikos
 */
public class CompatProjectConnection implements ProjectConnection {

    private ProjectConnection delegate;

    public CompatProjectConnection(ProjectConnection delegate) {
        this.delegate = delegate;
    }

    @Override
    public <T> T getModel(Class<T> modelType) throws GradleConnectionException, IllegalStateException {
        return injectCompatibilityModel(this.delegate.getModel(modelType));
    }

    @Override
    public <T> void getModel(Class<T> modelType, ResultHandler<? super T> handler) throws IllegalStateException {
        this.delegate.getModel(modelType, new CompatResultHandler<>(handler));
    }

    @Override
    public BuildLauncher newBuild() {
        return this.delegate.newBuild();
    }

    @Override
    public TestLauncher newTestLauncher() {
        return this.delegate.newTestLauncher();
    }

    @Override
    public <T> ModelBuilder<T> model(Class<T> modelType) {
        return new CompatModelBuilder<>(this.delegate.model(modelType));
    }

    @Override
    public <T> BuildActionExecuter<T> action(BuildAction<T> buildAction) {
        return new CompatBuildActionExecuter<>(this.delegate.action(buildAction));
    }

    @Override
    public Builder action() {
        return new CompatBuilder(this.delegate.action());
    }

    @Override
    public void close() {
        this.delegate.close();
    }

    @Override
    public void notifyDaemonsAboutChangedPaths(List<Path> changedPaths) {
        this.delegate.notifyDaemonsAboutChangedPaths(changedPaths);
    }

    @SuppressWarnings("unchecked")
    private static <T> T injectCompatibilityModel(T model) {
        if (model instanceof EclipseProject) {
            return (T) new CompatEclipseProject((EclipseProject) model);
        } else if (model instanceof Map<?, ?>) {
            Map<String, EclipseProject> compatModel = Maps.newLinkedHashMap();
            for (Entry<Object, Object> entry : ((Map<Object, Object>)model).entrySet()) {
                if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof EclipseProject)) {
                    return model;
                }
                String buildPath = (String) entry.getKey();
                EclipseProject eclipseProject = (EclipseProject) entry.getValue();
                compatModel.put(buildPath, (EclipseProject) injectCompatibilityModel(eclipseProject));
            }
            return (T) compatModel;
        } else {
            return model;
        }
    }

    private static class CompatResultHandler<T> implements ResultHandler<T> {

        private final ResultHandler<T> delegate;

        public CompatResultHandler(ResultHandler<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onComplete(T result) {
            this.delegate.onComplete(injectCompatibilityModel(result));
        }

        @Override
        public void onFailure(GradleConnectionException failure) {
            this.delegate.onFailure(failure);
        }
    }

    private static class CompatModelBuilder<T> implements ModelBuilder<T> {

        private final ModelBuilder<T> delegate;

        CompatModelBuilder(ModelBuilder<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public ModelBuilder<T> withArguments(String... arguments) {
            this.delegate.withArguments(arguments);
            return this;
        }

        @Override
        public ModelBuilder<T> withArguments(Iterable<String> arguments) {
            this.delegate.withArguments(arguments);
            return this;
        }

        @Override
        public ModelBuilder<T> addArguments(String... arguments) {
            this.delegate.addArguments(arguments);
            return this;
        }

        @Override
        public ModelBuilder<T> addArguments(Iterable<String> arguments) {
            this.delegate.addArguments(arguments);
            return this;
        }

        @Override
        public ModelBuilder<T> setStandardOutput(OutputStream outputStream) {
            this.delegate.setStandardOutput(outputStream);
            return this;
        }

        @Override
        public ModelBuilder<T> setStandardError(OutputStream outputStream) {
            this.delegate.setStandardError(outputStream);
            return this;
        }

        @Override
        public ModelBuilder<T> setColorOutput(boolean colorOutput) {
            this.delegate.setColorOutput(colorOutput);
            return this;
        }

        @Override
        public ModelBuilder<T> setStandardInput(InputStream inputStream) {
            this.delegate.setStandardInput(inputStream);
            return this;
        }

        @Override
        public ModelBuilder<T> setJavaHome(File javaHome) {
            this.delegate.setJavaHome(javaHome);
            return this;
        }

        @Override
        public ModelBuilder<T> setJvmArguments(String... jvmArguments) {
            this.delegate.setJvmArguments(jvmArguments);
            return this;
        }

        @Override
        public ModelBuilder<T> forTasks(String... tasks) {
            this.delegate.forTasks(tasks);
            return this;
        }

        @Override
        public ModelBuilder<T> setJvmArguments(Iterable<String> jvmArguments) {
            this.delegate.setJvmArguments(jvmArguments);
            return this;
        }

        @Override
        public ModelBuilder<T> addJvmArguments(String... jvmArguments) {
            this.delegate.addJvmArguments(jvmArguments);
            return this;
        }

        @Override
        public ModelBuilder<T> addJvmArguments(Iterable<String> jvmArguments) {
            this.delegate.addJvmArguments(jvmArguments);
            return this;
        }

        @Override
        public ModelBuilder<T> setEnvironmentVariables(Map<String, String> envVariables) {
            this.delegate.setEnvironmentVariables(envVariables);
            return this;
        }

        @Override
        public ModelBuilder<T> forTasks(Iterable<String> tasks) {
            this.delegate.forTasks(tasks);
            return this;
        }

        @Override
        public ModelBuilder<T> addProgressListener(ProgressListener listener) {
            this.delegate.addProgressListener(listener);
            return this;
        }

        @Override
        public ModelBuilder<T> addProgressListener(org.gradle.tooling.events.ProgressListener listener, Set<OperationType> eventTypes) {
            this.delegate.addProgressListener(listener, eventTypes);
            return this;
        }

        @Override
        public ModelBuilder<T> addProgressListener(org.gradle.tooling.events.ProgressListener listener) {
            this.delegate.addProgressListener(listener);
            return this;
        }

        @Override
        public ModelBuilder<T> addProgressListener(org.gradle.tooling.events.ProgressListener listener, OperationType... operationTypes) {
            this.delegate.addProgressListener(listener, operationTypes);
            return this;
        }

        @Override
        public ModelBuilder<T> withCancellationToken(CancellationToken cancellationToken) {
            this.delegate.withCancellationToken(cancellationToken);
            return this;
        }

        @Override
        public T get() throws GradleConnectionException, IllegalStateException {
            return injectCompatibilityModel(this.delegate.get());
        }

        @Override
        public void get(ResultHandler<? super T> handler) throws IllegalStateException {
            this.delegate.get(new CompatResultHandler<>(handler));
        }

        @Override
        public ModelBuilder<T> withSystemProperties(Map<String, String> systemProperties) {
            this.delegate.withSystemProperties(systemProperties);
            return this;
        }
    }

    private static class CompatBuildActionExecuter<T> implements BuildActionExecuter<T> {

        private final BuildActionExecuter<T> delegate;

        CompatBuildActionExecuter(BuildActionExecuter<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public BuildActionExecuter<T> withArguments(String... arguments) {
            this.delegate.withArguments(arguments);
            return this;
        }

        @Override
        public BuildActionExecuter<T> withArguments(Iterable<String> arguments) {
            this.delegate.withArguments(arguments);
            return this;
        }

        @Override
        public BuildActionExecuter<T> addArguments(String... arguments) {
            this.delegate.addArguments(arguments);
            return this;
        }

        @Override
        public BuildActionExecuter<T> addArguments(Iterable<String> arguments) {
            this.delegate.addArguments(arguments);
            return this;
        }

        @Override
        public BuildActionExecuter<T> setStandardOutput(OutputStream outputStream) {
            this.delegate.setStandardOutput(outputStream);
            return this;
        }

        @Override
        public BuildActionExecuter<T> setStandardError(OutputStream outputStream) {
            this.delegate.setStandardError(outputStream);
            return this;
        }

        @Override
        public BuildActionExecuter<T> setColorOutput(boolean colorOutput) {
            this.delegate.setColorOutput(colorOutput);
            return this;
        }

        @Override
        public BuildActionExecuter<T> setStandardInput(InputStream inputStream) {
            this.delegate.setStandardInput(inputStream);
            return this;
        }

        @Override
        public BuildActionExecuter<T> addProgressListener(org.gradle.tooling.events.ProgressListener listener) {
            this.delegate.addProgressListener(listener);
            return this;
        }

        @Override
        public BuildActionExecuter<T> addProgressListener(ProgressListener listener) {
            this.delegate.addProgressListener(listener);
            return this;
        }

        @Override
        public BuildActionExecuter<T> addProgressListener(org.gradle.tooling.events.ProgressListener listener, OperationType... operationTypes) {
            this.delegate.addProgressListener(listener, operationTypes);
            return this;
        }

        @Override
        public BuildActionExecuter<T> addProgressListener(org.gradle.tooling.events.ProgressListener listener, Set<OperationType> eventTypes) {
            this.delegate.addProgressListener(listener, eventTypes);
            return this;
        }

        @Override
        public BuildActionExecuter<T> setJavaHome(File javaHome) {
            this.delegate.setJavaHome(javaHome);
            return this;
        }

        @Override
        public BuildActionExecuter<T> setJvmArguments(String... jvmArguments) {
            this.delegate.setJvmArguments(jvmArguments);
            return this;
        }

        @Override
        public BuildActionExecuter<T> setJvmArguments(Iterable<String> jvmArguments) {
            this.delegate.setJvmArguments(jvmArguments);
            return this;
        }

        @Override
        public BuildActionExecuter<T> addJvmArguments(String... jvmArguments) {
            this.delegate.addJvmArguments(jvmArguments);
            return this;
        }

        @Override
        public BuildActionExecuter<T> addJvmArguments(Iterable<String> jvmArguments) {
            this.delegate.addJvmArguments(jvmArguments);
            return this;
        }

        @Override
        public BuildActionExecuter<T> setEnvironmentVariables(Map<String, String> envVariables) {
            this.delegate.setEnvironmentVariables(envVariables);
            return this;
        }

        @Override
        public BuildActionExecuter<T> forTasks(String... tasks) {
            this.delegate.forTasks(tasks);
            return this;
        }

        @Override
        public BuildActionExecuter<T> withCancellationToken(CancellationToken cancellationToken) {
            this.delegate.withCancellationToken(cancellationToken);
            return this;
        }

        @Override
        public BuildActionExecuter<T> forTasks(Iterable<String> tasks) {
            this.delegate.forTasks(tasks);
            return this;
        }

        @Override
        public T run() throws GradleConnectionException, IllegalStateException {
            return injectCompatibilityModel(this.delegate.run());
        }

        @Override
        public void run(ResultHandler<? super T> handler) throws IllegalStateException {
            this.delegate.run(new CompatResultHandler<>(handler));
        }

        @Override
        public BuildActionExecuter<T> withSystemProperties(Map<String, String> systemProperties) {
            this.delegate.withSystemProperties(systemProperties);
            return this;
        }

        @Override
        public void setStreamedValueListener(StreamedValueListener listener) {
            this.delegate.setStreamedValueListener(listener);
        }
    }

    private static class CompatIntermediateResultHander<T> implements IntermediateResultHandler<T> {

        private final IntermediateResultHandler<T> delegate;

        CompatIntermediateResultHander(IntermediateResultHandler<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onComplete(T result) {
            this.delegate.onComplete(injectCompatibilityModel(result));
        }
    }

    private static class CompatBuilder implements Builder {

        private final Builder delegate;

        CompatBuilder(Builder delegate) {
            this.delegate = delegate;
        }

        @Override
        public <T> Builder projectsLoaded(BuildAction<T> buildAction, IntermediateResultHandler<? super T> handler) throws IllegalArgumentException {
            this.delegate.projectsLoaded(buildAction, new CompatIntermediateResultHander<>(handler));
            return this;
        }

        @Override
        public <T> Builder buildFinished(BuildAction<T> buildAction, IntermediateResultHandler<? super T> handler) throws IllegalArgumentException {
            this.delegate.buildFinished(buildAction, new CompatIntermediateResultHander<>(handler));
            return this;
        }

        @Override
        public BuildActionExecuter<Void> build() {
            return this.delegate.build();
        }
    }
}
