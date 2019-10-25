/*******************************************************************************
 * Copyright (c) 2019 Gradle Inc.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.core.internal.util.progress;

import org.gradle.tooling.CancellationTokenSource;
import org.gradle.tooling.ProgressListener;
import org.gradle.tooling.events.ProgressEvent;

import org.eclipse.core.runtime.IProgressMonitor;

/**
 * Progress listener canceling the build if the progress monitor is cancelled.
 */
public class CancellationForwardingListener implements ProgressListener, org.gradle.tooling.events.ProgressListener {

    private final IProgressMonitor monitor;
    private final CancellationTokenSource tokenSource;
    private boolean cancelRequested;

    public CancellationForwardingListener(IProgressMonitor monitor, CancellationTokenSource tokenSource) {
        this.monitor = monitor;
        this.tokenSource = tokenSource;
    }

    @Override
    public void statusChanged(ProgressEvent ignore) {
        forwardCancellation();
    }

    @Override
    public void statusChanged(org.gradle.tooling.ProgressEvent ignore) {
        forwardCancellation();
    }

    private void forwardCancellation() {
        if (!this.cancelRequested && this.monitor.isCanceled()) {
            this.tokenSource.cancel();
            this.cancelRequested = true;
        }
    }
}
