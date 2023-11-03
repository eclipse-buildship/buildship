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

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.buildship.core.internal.CorePlugin;

/**
 * An {@link IResourceChangeListener} implementation which sends events about project change events
 * via {@link CorePlugin#listenerRegistry()}.
 *
 * @author Donat Csikos
 *
 */
public final class ProjectChangeListener implements IResourceChangeListener {

    private ProjectChangeListener() {
    }

    @Override
    public void resourceChanged(IResourceChangeEvent event) {
        IResourceDelta delta = event.getDelta();
        if (delta != null) {
            try {
                visitDelta(delta);
            } catch (CoreException e) {
                CorePlugin.logger().warn("Failed to detect project changes", e);
            }
        }
    }

    private void visitDelta(IResourceDelta delta) throws CoreException {
        delta.accept(new IResourceDeltaVisitor() {

            @Override
            public boolean visit(IResourceDelta delta) throws CoreException {
                try {
                    return doVisitDelta(delta);
                } catch (Exception e) {
                    throw new CoreException(new Status(IStatus.WARNING, CorePlugin.PLUGIN_ID, "ProjectChangeListener failed", e));
                }
            }
        });
    }

    private boolean doVisitDelta(IResourceDelta delta) throws Exception {
        if (delta.getResource() instanceof IProject) {
            IProject project = (IProject) delta.getResource();
            IPath fromPath = delta.getMovedFromPath();
            IPath toPath = delta.getMovedToPath();
            if (delta.getKind() == IResourceDelta.REMOVED) {
                if (fromPath == null && toPath == null) {
                    CorePlugin.listenerRegistry().dispatch(new ProjectDeletedEvent(project));
                }
            } else  if (delta.getKind() == IResourceDelta.ADDED) {
                if (fromPath == null && toPath == null) {
                    CorePlugin.listenerRegistry().dispatch(new ProjectCreatedEvent(project));
                } else if (fromPath != null) {
                    CorePlugin.listenerRegistry().dispatch(new ProjectMovedEvent(project, fromPath.lastSegment()));
                }
            } else if (0 != (delta.getFlags() & IResourceDelta.OPEN)) {
                if (project.isOpen()) {
                    CorePlugin.listenerRegistry().dispatch(new ProjectOpenedEvent(project));
                } else {
                    CorePlugin.listenerRegistry().dispatch(new ProjectClosedEvent(project));
                }
            }
            return false;
        } else {
            // don't traverse deeper than the project level
            return delta.getResource() instanceof IWorkspaceRoot;
        }
    }

    public static ProjectChangeListener createAndRegister() {
        ProjectChangeListener listener = new ProjectChangeListener();
        ResourcesPlugin.getWorkspace().addResourceChangeListener(listener, IResourceChangeEvent.POST_CHANGE);
        return listener;
    }

    public void close() {
        ResourcesPlugin.getWorkspace().removeResourceChangeListener(this);
    }
}
