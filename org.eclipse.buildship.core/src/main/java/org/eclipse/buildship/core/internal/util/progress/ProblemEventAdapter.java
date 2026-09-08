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
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.gradle.tooling.events.problems.ProblemContext;
import org.gradle.tooling.events.problems.ProblemDefinition;
import org.gradle.tooling.events.problems.ProblemGroup;
import org.gradle.tooling.events.problems.ProblemId;
import org.gradle.tooling.events.problems.SingleProblemEvent;
import org.gradle.tooling.events.problems.Solution;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;

import org.eclipse.buildship.core.internal.marker.GradleErrorMarker;

public abstract class ProblemEventAdapter implements Consumer<IMarker> {

    public static Consumer<IMarker> adapterFor(SingleProblemEvent event) {
        return new SingleProblemEventAdapter(event);
    }

    public static Consumer<IMarker> adapterFor(ProblemDefinition definition, ProblemContext context) {
        return new AggregationProblemEventAdapter(definition, context);
    }


    protected abstract ProblemId getId();

    protected abstract String getContextualLabel();

    protected abstract String getDetails();

    protected abstract List<Solution> getSolutions();

    protected abstract String getAdditionalData();

    protected abstract String getDocumentationUrl();

    @Override
    public final void accept(IMarker marker) {
        try {
            marker.setAttribute(GradleErrorMarker.ATTRIBUTE_ID_DISPLAY_NAME, getId().getDisplayName());
            marker.setAttribute(GradleErrorMarker.ATTRIBUTE_FQID, fqid(getId()));
            marker.setAttribute(GradleErrorMarker.ATTRIBUTE_LABEL, getContextualLabel());
            marker.setAttribute(GradleErrorMarker.ATTRIBUTE_DETAILS, getDetails());
            List<Solution> solutions = getSolutions();
            if (solutions != null) {
                String solutionsString = solutions.stream().map(Solution::getSolution).collect(Collectors.joining(System.getProperty("line.separator")));
                marker.setAttribute(GradleErrorMarker.ATTRIBUTE_SOLUTIONS, solutionsString);
            }
            marker.setAttribute(GradleErrorMarker.ATTRIBUTE_ADDITIONAL_DATA, getAdditionalData());
            String documentationLink = getDocumentationUrl();
            if (documentationLink != null) {
                marker.setAttribute(GradleErrorMarker.ATTRIBUTE_DOCUMENTATION_LINK, documentationLink);
            }
        } catch (CoreException e) {
            throw new RuntimeException(e);
        }

    }

    private static String fqid(ProblemId problemId) {
        return groupFqid(problemId.getGroup()) + ":" + problemId.getName();
    }

    private static String groupFqid(ProblemGroup group) {
        if (group.getParent() == null) {
            return group.getName();
        } else {
            return groupFqid(group.getParent()) + ":" + group.getName();
        }
    }

    private static class AggregationProblemEventAdapter extends ProblemEventAdapter {
        private final ProblemDefinition definition;
        private final ProblemContext context;

        public AggregationProblemEventAdapter(ProblemDefinition definition, ProblemContext context) {
            this.definition = definition;
            this.context = context;
        }

        @Override
        protected ProblemId getId() {
            return this.definition.getId();
        }

        @Override
        protected String getContextualLabel() {
            return this.context.getDetails().getDetails();
        }

        @Override
        protected String getDetails() {
            return this.context.getDetails().getDetails();
        }

        @Override
        protected List<Solution> getSolutions() {
           return this.context.getSolutions();
        }

        @Override
        protected String getAdditionalData() {
            return "";
        }

        @Override
        protected String getDocumentationUrl() {
            return this.definition.getDocumentationLink().getUrl();
        }
    }

    private static class SingleProblemEventAdapter extends ProblemEventAdapter {

        private final SingleProblemEvent problem;

        public SingleProblemEventAdapter(SingleProblemEvent problem) {
            this.problem = problem;
        }

        @Override
        protected ProblemId getId() {
            return this.problem.getDefinition().getId();
        }

        @Override
        protected String getContextualLabel() {
            return this.problem.getContextualLabel().getContextualLabel();
        }

        @Override
        protected String getDetails() {
            return this.problem.getDetails().getDetails();
        }

        @Override
        protected List<Solution> getSolutions() {
            return this.problem.getSolutions();
        }

        @Override
        protected String getAdditionalData() {
            return mapToMultiLineString(this.problem.getAdditionalData().getAsMap());
        }

        private static String mapToMultiLineString (Map<String, Object> map) {
            StringBuilder result = new StringBuilder();
            String separator = "";
            for (Entry<String, Object> entry : map.entrySet()) {
                result.append(separator);
                result.append(entry.getKey());
                result.append(": ");
                result.append(entry.getValue());
                separator = "\n";
            }
            return result.toString();
        }

        @Override
        protected String getDocumentationUrl() {
            return this.problem.getDefinition().getDocumentationLink().getUrl();
        }
    }

}
