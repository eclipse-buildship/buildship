/*
 * Copyright (c) 2016 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 */
package org.eclipse.buildship.core.workspace.internal;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

import com.gradleware.tooling.toolingmodel.repository.ModelRepository;

import org.eclipse.buildship.core.CorePlugin;
import org.eclipse.buildship.core.configuration.BuildConfiguration;
import org.eclipse.buildship.core.util.progress.AsyncHandler;
import org.eclipse.buildship.core.workspace.GradleBuild;
import org.eclipse.buildship.core.workspace.ModelProvider;
import org.eclipse.buildship.core.workspace.NewProjectHandler;

/**
 * Default implementation of {@link GradleBuild}.
 *
 * @author Stefan Oehme
 */
public class DefaultGradleBuild implements GradleBuild {

    private final BuildConfiguration buildConfig;

    public DefaultGradleBuild(BuildConfiguration buildConfig) {
        this.buildConfig = Preconditions.checkNotNull(buildConfig);
    }

    @Override
    public void synchronize() {
        synchronize(NewProjectHandler.NO_OP);
    }
    @Override
    public void synchronize(NewProjectHandler newProjectHandler) {
        synchronize(newProjectHandler, AsyncHandler.NO_OP);
    }
    @Override
    public void synchronize(NewProjectHandler newProjectHandler, AsyncHandler initializer) {
        SynchronizeGradleBuildsJob.forSingleGradleBuild(this, newProjectHandler, initializer).schedule();
    }

    @Override
    public ModelProvider getModelProvider() {
        ModelRepository modelRepository = CorePlugin.modelRepositoryProvider().getModelRepository(this.buildConfig.toRequestAttributes());
        return new DefaultModelProvider(modelRepository);
    }

    @Override
    public BuildConfiguration getBuildConfig() {
        return this.buildConfig;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof DefaultGradleBuild) {
            DefaultGradleBuild other = (DefaultGradleBuild) obj;
            return Objects.equal(this.buildConfig, other.buildConfig);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.buildConfig);
    }
}
