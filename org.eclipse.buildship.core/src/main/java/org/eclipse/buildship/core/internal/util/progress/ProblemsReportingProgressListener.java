/*******************************************************************************
 * Copyright (c) 2023 Gradle Inc. and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.core.internal.util.progress;

import java.util.List;
import java.util.function.Consumer;

import org.gradle.tooling.Failure;
import org.gradle.tooling.events.ProgressEvent;
import org.gradle.tooling.events.ProgressListener;
import org.gradle.tooling.events.problems.FileLocation;
import org.gradle.tooling.events.problems.LineInFileLocation;
import org.gradle.tooling.events.problems.Location;
import org.gradle.tooling.events.problems.OffsetInFileLocation;
import org.gradle.tooling.events.problems.ProblemAggregation;
import org.gradle.tooling.events.problems.ProblemAggregationEvent;
import org.gradle.tooling.events.problems.ProblemContext;
import org.gradle.tooling.events.problems.ProblemDefinition;
import org.gradle.tooling.events.problems.ProblemEvent;
import org.gradle.tooling.events.problems.SingleProblemEvent;

import com.google.common.base.Strings;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

import org.eclipse.buildship.core.internal.CorePlugin;
import org.eclipse.buildship.core.internal.marker.GradleErrorMarker;
import org.eclipse.buildship.core.internal.workspace.InternalGradleBuild;

public class ProblemsReportingProgressListener implements ProgressListener {

    private InternalGradleBuild gradleBuild;

    public ProblemsReportingProgressListener(InternalGradleBuild gradleBuild) {
        this.gradleBuild = gradleBuild;
    }

    @Override
    public void statusChanged(ProgressEvent event) {
        if (event instanceof ProblemEvent) {
            ProblemEvent problemEvent = (ProblemEvent) event;
            try {
                if (problemEvent instanceof SingleProblemEvent) {
                    reportProblem((SingleProblemEvent) problemEvent);
                } else if (problemEvent instanceof ProblemAggregationEvent) {
                    reportProblem((ProblemAggregationEvent) problemEvent);
                }
            } catch (Exception e) {
                CorePlugin.logger().warn("Cannot report problem " + problemEvent, e);
            }
        }
    }

    private void reportProblem(SingleProblemEvent event) {
        List<Location> locations = event.getLocations();
        GradleErrorMarker.createProblemMarker(
            toMarkerSeverity(event.getDefinition().getSeverity()),
            findMarkerResource(locations),
            this.gradleBuild,
            markerMessage(event),
            stacktraceStringFor(event.getFailure().getFailure()),
            markerPositionConfiguration(locations),
            ProblemEventAdapter.adapterFor(event)
        );
    }

    private void reportProblem(ProblemAggregationEvent event) {
        ProblemAggregation aggregation = event.getProblemAggregation();
        ProblemDefinition definition = aggregation.getDefinition();
        List<ProblemContext> contexts = aggregation.getProblemContext();
        for (ProblemContext context : contexts) {
            List<Location> locations = context.getLocations();
            GradleErrorMarker.createProblemMarker(
                toMarkerSeverity(definition.getSeverity()),
                findMarkerResource(locations),
                this.gradleBuild,
                markerMessage(context.getDetails().getDetails(), context.getDetails().getDetails(), definition.getId().getDisplayName()),
                stacktraceStringFor(context.getFailure().getFailure()),
                markerPositionConfiguration(locations),
                ProblemEventAdapter.adapterFor(definition, context)
            );
        }
    }

    private IResource findMarkerResource(List<Location> locations) {
        return locations.stream()
                .filter(FileLocation.class::isInstance)
                .map(FileLocation.class::cast).findFirst()
                .map(fl -> toResource(fl))
                .orElseGet(() -> {
                    IResource result = ResourcesPlugin.getWorkspace().getRoot().getFileForLocation(Path.fromOSString(rootProjectPath()));
                    return result == null ? ResourcesPlugin.getWorkspace().getRoot() : result;
                });
    }

    private String rootProjectPath() {
        return ProblemsReportingProgressListener.this.gradleBuild.getBuildConfig().getRootProjectDirectory().getAbsolutePath();
    }

    private Consumer<IMarker> markerPositionConfiguration(List<Location> locations) {
        for (Location location : locations) {
            if (location instanceof OffsetInFileLocation) {
                return  marker -> {
                    OffsetInFileLocation offsetLocation = ((OffsetInFileLocation) location);
                    int startOffset = offsetLocation.getOffset();
                    int endOffset =  offsetLocation.getLength();
                    try {
                        marker.setAttribute(IMarker.CHAR_START, startOffset);
                        marker.setAttribute(IMarker.CHAR_END, startOffset + endOffset);
                    } catch (CoreException e) {
                        throw new RuntimeException(e);
                    }
                };
            } else if (location instanceof LineInFileLocation) {
                return marker -> {

                    Integer lineNumber = lineNumberOf((FileLocation) location);
                    if (lineNumber >= 0) {
                        try {
                            marker.setAttribute(IMarker.LINE_NUMBER, lineNumber);
                        } catch (CoreException e) {
                            throw new RuntimeException(e);
                        }
                    }
                };
            }
        }
         return notUsed -> {};
    }

    private static String markerMessage(SingleProblemEvent problem) {
        String result = problem.getContextualLabel().getContextualLabel();
        if (result == null) {
            result = problem.getDetails().getDetails();
        }
        if (result == null) {
            result = problem.getDefinition().getId().getDisplayName();
        }

        return Strings.nullToEmpty(result);
    }

    private static String markerMessage(String contextualLabel, String details, String definitionDisplayName) {
        String result = contextualLabel;
        if (result == null) {
            result = details;
        }
        if (result == null) {
            result = definitionDisplayName;
        }

        return Strings.nullToEmpty(result);
    }

    private static String stacktraceStringFor(Failure failure) {
        if (failure == null) {
            return null;
        }
        return failure.getDescription();
    }

    private static IResource toResource(FileLocation location) {
        IPath absolutePath = Path.fromOSString(location.getPath());
        IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
        IFile workspacePath = workspaceRoot.getFileForLocation(absolutePath);
        if (workspacePath != null && workspacePath.exists()) {
            return workspacePath;
        }
        return ResourcesPlugin.getWorkspace().getRoot();
    }

    private static Integer lineNumberOf(FileLocation location) {
        if (location instanceof LineInFileLocation) {
            if (((LineInFileLocation) location).getLine() > 0) {
                return ((LineInFileLocation) location).getLine();
            }
        }
        return -1;
    }

    public int toMarkerSeverity(org.gradle.tooling.events.problems.Severity severity) {
        if (severity == org.gradle.tooling.events.problems.Severity.ERROR) {
            return IMarker.SEVERITY_ERROR;
        } else if (severity == org.gradle.tooling.events.problems.Severity.ADVICE) {
            return IMarker.SEVERITY_INFO;
        } else {
            return IMarker.SEVERITY_WARNING;
        }
    }
}
