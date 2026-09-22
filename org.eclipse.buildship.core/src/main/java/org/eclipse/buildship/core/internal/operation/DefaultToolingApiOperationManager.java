/*******************************************************************************
 * Copyright (c) 2023 Gradle Inc. and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.core.internal.operation;

import java.util.concurrent.TimeUnit;

import org.gradle.tooling.CancellationTokenSource;
import org.gradle.tooling.GradleConnector;

import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.buildship.core.internal.util.progress.RateLimitingProgressMonitor;

/**
 * Default {@link ToolingApiOperationManager} implementation.
 *
 * @author Donat Csikos
 */
public final class DefaultToolingApiOperationManager implements ToolingApiOperationManager {

    @Override
    public void run(ToolingApiOperation runnable, IProgressMonitor monitor) throws CoreException {
        run(runnable, GradleConnector.newCancellationTokenSource(), monitor);
    }

    @Override
    public void run(ToolingApiOperation runnable, CancellationTokenSource tokenSource, IProgressMonitor monitor) throws CoreException {
        IProgressMonitor efficientMonitor = new RateLimitingProgressMonitor(monitor, 500, TimeUnit.MILLISECONDS);
        try {
            ResourcesPlugin.getWorkspace().run(new WorkspaceRunnableAdapter(runnable, tokenSource), runnable.getRule(), 0, efficientMonitor);
        } finally {
            efficientMonitor.done();
        }
    }

    /**
     * Adapts the target operation into a {@code IWorkspaceRunnable}.
     */
    private static class WorkspaceRunnableAdapter implements IWorkspaceRunnable {

        private final ToolingApiOperation runnable;
        private final CancellationTokenSource tokenSource;

        private WorkspaceRunnableAdapter(ToolingApiOperation runnable, CancellationTokenSource tokenSource) {
            this.runnable = runnable;
            this.tokenSource = tokenSource;
        }

        @Override
        public void run(IProgressMonitor monitor) throws CoreException {
            try {
                this.runnable.runInToolingApi(this.tokenSource, monitor);
            } catch (Exception e) {
                throw new CoreException(ToolingApiStatus.from(this.runnable.getName(), e));
            }
        }
    }
}
