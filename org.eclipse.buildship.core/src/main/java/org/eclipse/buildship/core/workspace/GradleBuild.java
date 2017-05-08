/*
 * Copyright (c) 2016 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 */
package org.eclipse.buildship.core.workspace;

import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.TestLauncher;

import com.gradleware.tooling.toolingmodel.repository.TransientRequestAttributes;

import org.eclipse.buildship.core.configuration.BuildConfiguration;
import org.eclipse.buildship.core.configuration.RunConfiguration;
//TODO this should eventually also contain the methods to launch tasks etc.
import org.eclipse.buildship.core.util.progress.AsyncHandler;

/*
 * TODO The methods in this class highlight some problems with our current job-centric design:
 * - callers can't decide for themselves how they want to handle errors
 * - callers cannot embed any of the operations in another operation (providing cancellation etc.)
 * - callers do not know if and when the operations finish
 *
 * This API should evolve into a set of synchronous operations that provide progress,
 * cancellation and throw CoreExceptions with detailed IStatus, which will allow
 * any problems to be displayed to the user in the most accurate fashion.
 */

/**
 * A Gradle build.
 *
 * @author Stefan Oehme
 */
public interface GradleBuild {

    /**
     * Attempts to synchronize the build with the workspace.
     * <p/>
     * The synchronization happens asynchronously. In case of a failure, the user will be notified
     * once after the build has finished.
     * <p/>
     * This is equivalent to calling {@code synchronize(NewProjectHandler.NO_OP)}
     */
    void synchronize();

    /**
     * Attempts to synchronize the build with the workspace.
     * <p/>
     * The synchronization happens asynchronously. In case of a failure, the user will be notified
     * once the build has finished.
     * <p/>
     * This is equivalent to calling {@code synchronize(newProjectHandler, AsyncHandler.NO_OP)}
     *
     * @param newProjectHandler how to handle newly added projects
     */
    void synchronize(NewProjectHandler newProjectHandler);

    /**
     * Attempts to synchronize the build with the workspace.
     * <p/>
     * The synchronization happens asynchronously. In case of a failure, the user will be notified
     * once the build has finished.
     *
     * @param newProjectHandler how to handle newly added projects
     * @param initializer an initializer to run before synchronization, e.g. to create a new project
     */
    void synchronize(NewProjectHandler newProjectHandler, AsyncHandler initializer);

    /**
     * Returns {@code true} if a synchronization job is already running for the same root project.
     *
     * @return true if a synchronization is running.
     */
    boolean isSyncRunning();

    /**
     * Returns the model provider for this build.
     *
     * @return the model provider, never null
     */
    ModelProvider getModelProvider();

    /**
     * Creates a new Gradle build launcher. The method automatically opens a new Tooling API
     * connection which is closed after the {@code run()} method is finished.
     *
     * @param runConfiguration the run configuration to configure the connection with
     * @param transientAttributes the transient attributes for the launcher.
     * @return the build launcher
     */
    BuildLauncher newBuildLauncher(RunConfiguration runConfiguration, TransientRequestAttributes transientAttributes);

    /**
     * Creates a new Gradle test launcher. The method automatically opens a new Tooling API
     * connection which is closed after the {@code run()} method is finished.
     *
     * @param transientAttributes the transient attributes for the launcher.
     * @return the test launcher
     */
    TestLauncher newTestLauncher(RunConfiguration runConfiguration, TransientRequestAttributes transientAttributes);

    /**
     * Returns build config used for this build.
     *
     * @return the build config, never null
     */
    BuildConfiguration getBuildConfig();
}
