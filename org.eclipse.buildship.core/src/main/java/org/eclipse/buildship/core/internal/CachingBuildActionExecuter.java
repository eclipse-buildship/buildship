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
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildActionExecuter;
import org.gradle.tooling.CancellationToken;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.ProgressListener;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.StreamedValueListener;
import org.gradle.tooling.events.OperationType;

import com.google.common.cache.Cache;
import com.google.common.collect.Lists;

import org.eclipse.buildship.core.internal.util.gradle.InspectableResultHandler;

public class CachingBuildActionExecuter<T> implements BuildActionExecuter<T> {

    private final BuildActionExecuter<T> delegate;
    private final Cache<Object, Object> cache;
    private final CacheKey.Builder cacheKeyBuilder;

    CachingBuildActionExecuter(BuildActionExecuter<T> delegate, BuildAction<T> buildAction, Cache<Object, Object> cache) {
        this.delegate = delegate;
        this.cache = cache;
        this.cacheKeyBuilder = CacheKey.builder().setBuildAction(buildAction);
    }

    @Override
    public CachingBuildActionExecuter<T> withArguments(String... arguments) {
        this.cacheKeyBuilder.setArguments(Arrays.asList(arguments));
        this.delegate.withArguments(arguments);
        return this;
    }

    @Override
    public CachingBuildActionExecuter<T> withArguments(Iterable<String> arguments) {
        this.cacheKeyBuilder.setArguments(arguments == null ? null : Lists.newArrayList(arguments));
        this.delegate.withArguments(arguments);
        return this;
    }

    @Override
    public CachingBuildActionExecuter<T> addArguments(String... arguments) {
        this.cacheKeyBuilder.addArguments(Arrays.asList(arguments));
        this.delegate.addArguments(arguments);
        return this;
    }

    @Override
    public CachingBuildActionExecuter<T> addArguments(Iterable<String> arguments) {
        this.cacheKeyBuilder.addArguments(Lists.newArrayList(arguments));
        this.delegate.addArguments(arguments);
        return this;
    }

    @Override
    public CachingBuildActionExecuter<T> setStandardOutput(OutputStream outputStream) {
        this.cacheKeyBuilder.markInvalid();
        this.delegate.setStandardOutput(outputStream);
        return this;
    }

    @Override
    public CachingBuildActionExecuter<T> setStandardError(OutputStream outputStream) {
        this.cacheKeyBuilder.markInvalid();
        this.delegate.setStandardError(outputStream);
        return this;
    }

    @Override
    public CachingBuildActionExecuter<T> setColorOutput(boolean colorOutput) {
        this.cacheKeyBuilder.markInvalid();
        this.delegate.setColorOutput(colorOutput);
        return this;
    }

    @Override
    public CachingBuildActionExecuter<T> setStandardInput(InputStream inputStream) {
        this.cacheKeyBuilder.markInvalid();
        this.delegate.setStandardInput(inputStream);
        return this;
    }

    @Override
    public CachingBuildActionExecuter<T> setJavaHome(File javaHome) {
        this.cacheKeyBuilder.setJavaHome(javaHome);
        this.delegate.setJavaHome(javaHome);
        return this;
    }

    @Override
    public CachingBuildActionExecuter<T> setJvmArguments(String... jvmArguments) {
        this.cacheKeyBuilder.setJvmArguments(Arrays.asList(jvmArguments));
        this.delegate.setJvmArguments(jvmArguments);
        return this;
    }

    @Override
    public CachingBuildActionExecuter<T> setJvmArguments(Iterable<String> jvmArguments) {
        this.cacheKeyBuilder.setJvmArguments(jvmArguments == null ? null : Lists.newArrayList(jvmArguments));
        this.delegate.setJvmArguments(jvmArguments);
        return this;
    }

    @Override
    public CachingBuildActionExecuter<T> addJvmArguments(String... jvmArguments) {
        this.cacheKeyBuilder.addJvmArguments(Arrays.asList(jvmArguments));
        this.delegate.addJvmArguments(jvmArguments);
        return this;
    }

    @Override
    public CachingBuildActionExecuter<T> addJvmArguments(Iterable<String> jvmArguments) {
        this.cacheKeyBuilder.addJvmArguments(Lists.newArrayList(jvmArguments));
        this.delegate.addJvmArguments(jvmArguments);
        return this;
    }

    @Override
    public CachingBuildActionExecuter<T> setEnvironmentVariables(Map<String, String> envVariables) {
        this.cacheKeyBuilder.setEnvironmentVariables(envVariables);
        this.delegate.setEnvironmentVariables(envVariables);
        return this;
    }

    @Override
    public CachingBuildActionExecuter<T> addProgressListener(ProgressListener listener) {
        this.cacheKeyBuilder.markInvalid();
        this.delegate.addProgressListener(listener);
        return this;
    }

    @Override
    public CachingBuildActionExecuter<T> addProgressListener(org.gradle.tooling.events.ProgressListener listener) {
        this.cacheKeyBuilder.markInvalid();
        this.delegate.addProgressListener(listener);
        return this;
    }

    @Override
    public CachingBuildActionExecuter<T> addProgressListener(org.gradle.tooling.events.ProgressListener listener, Set<OperationType> eventTypes) {
        this.cacheKeyBuilder.markInvalid();
        this.delegate.addProgressListener(listener, eventTypes);
        return this;
    }

    @Override
    public CachingBuildActionExecuter<T> addProgressListener(org.gradle.tooling.events.ProgressListener listener, OperationType... operationTypes) {
        this.cacheKeyBuilder.markInvalid();
        this.delegate.addProgressListener(listener, operationTypes);
        return this;
    }

    @Override
    public CachingBuildActionExecuter<T> withCancellationToken(CancellationToken cancellationToken) {
        this.delegate.withCancellationToken(cancellationToken);
        return this;
    }

    @Override
    public CachingBuildActionExecuter<T> forTasks(String... tasks) {
        this.cacheKeyBuilder.setTasks(Arrays.asList(tasks));
        this.delegate.forTasks(tasks);
        return this;
    }

    @Override
    public CachingBuildActionExecuter<T> forTasks(Iterable<String> tasks) {
        this.cacheKeyBuilder.setTasks(tasks == null ? null : Lists.newArrayList(tasks));
        this.delegate.forTasks(tasks);
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T run() throws GradleConnectionException, IllegalStateException {
        CacheKey key = this.cacheKeyBuilder.build();
        Object cachedValue = key.isInvalid() ? null : this.cache.getIfPresent(key);
        if (cachedValue != null) {
            return (T) cachedValue;
        } else {
            T result = this.delegate.run();
            this.cache.put(key, result);
            return result;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void run(ResultHandler<? super T> handler) throws IllegalStateException {
        CacheKey key = this.cacheKeyBuilder.build();
        Object cachedValue = key.isInvalid() ? null : this.cache.getIfPresent(key);
        if (cachedValue != null) {
            handler.onComplete((T) cachedValue);
        } else {
            InspectableResultHandler<T> inspectableResultHandler = new InspectableResultHandler<>();
            this.delegate.run(inspectableResultHandler);
            inspectableResultHandler.getResult().ifPresent(r -> this.cache.put(key, r));
            inspectableResultHandler.forwardResults(handler);
        }
    }

    @Override
    public BuildActionExecuter<T> withSystemProperties(Map<String, String> systemProperties) {
        this.cacheKeyBuilder.withSystemPrperties(systemProperties);
        this.delegate.withSystemProperties(systemProperties);
        return this;
    }

    @Override
    public void setStreamedValueListener(StreamedValueListener listener) {
        this.delegate.setStreamedValueListener(listener);
    }
}
