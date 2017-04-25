/*
 * Copyright (c) 2016 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.buildship.core.workspace.internal;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;

import com.gradleware.tooling.toolingmodel.repository.FixedRequestAttributes;

import org.eclipse.core.resources.IProject;

import org.eclipse.buildship.core.CorePlugin;
import org.eclipse.buildship.core.GradlePluginsRuntimeException;
import org.eclipse.buildship.core.configuration.GradleProjectNature;
import org.eclipse.buildship.core.configuration.ProjectConfiguration;
import org.eclipse.buildship.core.configuration.ProjectConfiguration.ConversionStrategy;
import org.eclipse.buildship.core.workspace.GradleBuild;
import org.eclipse.buildship.core.workspace.GradleBuilds;
import org.eclipse.buildship.core.workspace.GradleWorkspaceManager;

/**
 * Default implementation of {@link GradleWorkspaceManager}.
 *
 * @author Stefan Oehme
 */
public class DefaultGradleWorkspaceManager implements GradleWorkspaceManager {

    private final LoadingCache<FixedRequestAttributes, GradleBuild> cache = CacheBuilder.newBuilder().build(new CacheLoader<FixedRequestAttributes, GradleBuild>() {

        @Override
        public GradleBuild load(FixedRequestAttributes attributes) {
            return new DefaultGradleBuild(attributes);
        }});

    @Override
    public GradleBuild getGradleBuild(FixedRequestAttributes attributes) {
        return this.cache.getUnchecked(attributes);
    }

    @Override
    public Optional<GradleBuild> getGradleBuild(IProject project) {
        Optional<ProjectConfiguration> configuration = CorePlugin.projectConfigurationManager().tryReadProjectConfiguration(project);
        if (configuration.isPresent()) {
            return Optional.<GradleBuild>of(new DefaultGradleBuild(configuration.get().toRequestAttributes(ConversionStrategy.MERGE_PROJECT_SETTINGS)));
        } else {
            return Optional.absent();
        }
    }

    @Override
    public GradleBuilds getGradleBuilds() {
        Set<FixedRequestAttributes> attributes = attributesFor(CorePlugin.workspaceOperations().getAllProjects());
        return new DefaultGradleBuilds(getBuilds(attributes));
    }

    @Override
    public GradleBuilds getGradleBuilds(Set<IProject> projects) {
        Set<GradleBuild> gradleBuilds = getBuilds(attributesFor(projects));
        return new DefaultGradleBuilds(gradleBuilds);
    }

    private Set<GradleBuild> getBuilds(Set<FixedRequestAttributes> attributes) {
        try {
            return ImmutableSet.copyOf(this.cache.getAll(attributes).values());
        } catch (ExecutionException e) {
            throw new GradlePluginsRuntimeException(e);
        }
    }

    private static Set<FixedRequestAttributes> attributesFor(Collection<IProject> projects) {
        return FluentIterable.from(projects).filter(GradleProjectNature.isPresentOn()).transform(new Function<IProject, FixedRequestAttributes>() {

            @Override
            public FixedRequestAttributes apply(IProject project) {
                Optional<ProjectConfiguration> configuration = CorePlugin.projectConfigurationManager().tryReadProjectConfiguration(project);
                return configuration.isPresent() ? configuration.get().toRequestAttributes(ConversionStrategy.MERGE_PROJECT_SETTINGS) : null;
            }
        }).filter(Predicates.notNull()).toSet();
    }

}
