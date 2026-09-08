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

import org.gradle.tooling.CancellationToken;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ProgressListener;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.events.OperationType;

import com.google.common.cache.Cache;
import com.google.common.collect.Lists;

import org.eclipse.buildship.core.internal.CacheKey.Builder;
import org.eclipse.buildship.core.internal.util.gradle.InspectableResultHandler;

final class CachingModelBuilder<T> implements ModelBuilder<T> {

    private final ModelBuilder<T> delegate;
    private final Cache<Object, Object> cache;
    private final Builder cacheKeyBuilder;

    CachingModelBuilder(ModelBuilder<T> delegate, Cache<Object, Object> cache, Class<T> modelType) {
        this.delegate = delegate;
        this.cache = cache;
        this.cacheKeyBuilder = CacheKey.builder().setModelType(modelType);
    }

    @Override
    public CachingModelBuilder<T> withArguments(String... arguments) {
        this.cacheKeyBuilder.setArguments(Arrays.asList(arguments));
        this.delegate.withArguments(arguments);
        return this;
    }

    @Override
    public CachingModelBuilder<T> withArguments(Iterable<String> arguments) {
        this.cacheKeyBuilder.setArguments(arguments == null ? null : Lists.newArrayList(arguments));
        this.delegate.withArguments(arguments);
        return this;
    }

    @Override
    public ModelBuilder<T> addArguments(String... arguments) {
        this.cacheKeyBuilder.addArguments(Arrays.asList(arguments));
        this.delegate.addArguments(arguments);
        return this;
    }

    @Override
    public ModelBuilder<T> addArguments(Iterable<String> arguments) {
        this.cacheKeyBuilder.addArguments(Lists.newArrayList(arguments));
        this.delegate.addArguments(arguments);
        return this;
    }

    @Override
    public CachingModelBuilder<T> setStandardOutput(OutputStream outputStream) {
        this.cacheKeyBuilder.markInvalid();
        this.delegate.setStandardOutput(outputStream);
        return this;
    }

    @Override
    public CachingModelBuilder<T> setStandardError(OutputStream outputStream) {
        this.cacheKeyBuilder.markInvalid();
        this.delegate.setStandardError(outputStream);
        return this;
    }

    @Override
    public CachingModelBuilder<T> setColorOutput(boolean colorOutput) {
        this.cacheKeyBuilder.markInvalid();
        this.delegate.setColorOutput(colorOutput);
        return this;
    }

    @Override
    public CachingModelBuilder<T> setStandardInput(InputStream inputStream) {
        this.cacheKeyBuilder.markInvalid();
        this.delegate.setStandardInput(inputStream);
        return this;
    }

    @Override
    public CachingModelBuilder<T> setJavaHome(File javaHome) {
        this.cacheKeyBuilder.setJavaHome(javaHome);
        this.delegate.setJavaHome(javaHome);
        return this;
    }

    @Override
    public CachingModelBuilder<T> setJvmArguments(String... jvmArguments) {
        this.cacheKeyBuilder.setJvmArguments(Arrays.asList(jvmArguments));
        this.delegate.setJvmArguments(jvmArguments);
        return this;
    }

    @Override
    public CachingModelBuilder<T> setJvmArguments(Iterable<String> jvmArguments) {
        this.cacheKeyBuilder.setJvmArguments(jvmArguments == null ? null : Lists.newArrayList(jvmArguments));
        this.delegate.setJvmArguments(jvmArguments);
        return this;
    }


    @Override
    public ModelBuilder<T> addJvmArguments(String... jvmArguments) {
        this.cacheKeyBuilder.addJvmArguments(Arrays.asList(jvmArguments));
        this.delegate.addJvmArguments(jvmArguments);
        return this;
    }

    @Override
    public ModelBuilder<T> addJvmArguments(Iterable<String> jvmArguments) {
        this.cacheKeyBuilder.addJvmArguments(Lists.newArrayList(jvmArguments));
        this.delegate.addJvmArguments(jvmArguments);
        return this;
    }

    @Override
    public CachingModelBuilder<T> setEnvironmentVariables(Map<String, String> envVariables) {
        this.cacheKeyBuilder.setEnvironmentVariables(envVariables);
        this.delegate.setEnvironmentVariables(envVariables);
        return this;
    }

    @Override
    public CachingModelBuilder<T> addProgressListener(ProgressListener listener) {
        this.cacheKeyBuilder.markInvalid();
        this.delegate.addProgressListener(listener);
        return this;
    }

    @Override
    public CachingModelBuilder<T> addProgressListener(org.gradle.tooling.events.ProgressListener listener) {
        this.cacheKeyBuilder.markInvalid();
        this.delegate.addProgressListener(listener);
        return this;
    }

    @Override
    public CachingModelBuilder<T> addProgressListener(org.gradle.tooling.events.ProgressListener listener, Set<OperationType> eventTypes) {
        this.cacheKeyBuilder.markInvalid();
        this.delegate.addProgressListener(listener, eventTypes);
        return this;
    }

    @Override
    public CachingModelBuilder<T> addProgressListener(org.gradle.tooling.events.ProgressListener listener, OperationType... operationTypes) {
        this.cacheKeyBuilder.markInvalid();
        this.delegate.addProgressListener(listener, operationTypes);
        return this;
    }

    @Override
    public CachingModelBuilder<T> withCancellationToken(CancellationToken cancellationToken) {
        this.delegate.withCancellationToken(cancellationToken);
        return this;
    }

    @Override
    public CachingModelBuilder<T> forTasks(String... tasks) {
        this.cacheKeyBuilder.setTasks(Arrays.asList(tasks));
        this.delegate.forTasks(tasks);
        return this;
    }

    @Override
    public CachingModelBuilder<T> forTasks(Iterable<String> tasks) {
        this.cacheKeyBuilder.setTasks(tasks == null ? null : Lists.newArrayList(tasks));
        this.delegate.forTasks(tasks);
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T get() throws GradleConnectionException, IllegalStateException {
        CacheKey key = this.cacheKeyBuilder.build();
        Object cachedValue = key.isInvalid() ? null : this.cache.getIfPresent(key);
        if (cachedValue != null) {
            return (T) cachedValue;
        } else {
            T result = this.delegate.get();
            this.cache.put(key, result);
            return result;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void get(ResultHandler<? super T> handler) throws IllegalStateException {
        CacheKey key = this.cacheKeyBuilder.build();
        Object cachedValue = key.isInvalid() ? null : this.cache.getIfPresent(key);
        if (cachedValue != null) {
            handler.onComplete((T) cachedValue);
        } else {
            InspectableResultHandler<T> inspectableResultHandler = new InspectableResultHandler<>();
            this.delegate.get(inspectableResultHandler);
            inspectableResultHandler.getResult().ifPresent(r -> this.cache.put(key, r));
            inspectableResultHandler.forwardResults(handler);
        }
    }

    @Override
    public CachingModelBuilder<T> withSystemProperties(Map<String, String> systemProperties) {
        this.cacheKeyBuilder.withSystemPrperties(systemProperties);
        this.delegate.withSystemProperties(systemProperties);
        return this;
    }
}
