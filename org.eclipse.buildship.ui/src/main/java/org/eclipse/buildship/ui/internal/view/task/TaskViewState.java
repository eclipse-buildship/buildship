/*******************************************************************************
 * Copyright (c) 2023 Gradle Inc. and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.ui.internal.view.task;

import org.osgi.service.prefs.BackingStoreException;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;

import org.eclipse.buildship.core.internal.util.preference.EclipsePreferencesUtils;
import org.eclipse.buildship.ui.internal.UiPlugin;

/**
 * Represents the (persistable) configuration state of the {@link TaskView}. Backed by the Eclipse
 * Preferences API.
 */
public final class TaskViewState {

    private static final String PREF_PROJECT_TASKS_VISIBLE = "tasksView.projectTasksVisible";
    private static final String PREF_TASK_SELECTORS_VISIBLE = "tasksView.taskSelectorsVisible";
    private static final String PREF_PRIVATE_TASKS_VISIBLE = "tasksView.privateTasksVisible";
    private static final String PREF_SORT_BY_TYPE = "tasksView.sortByType";
    private static final String PREF_SORT_BY_VISIBILITY = "tasksView.sortByVisibility";
    private static final String PREF_LINK_TO_SELECTION = "tasksView.linkToSelection";
    private static final String PREF_HEADER_NAME_COLUMN_WIDTH = "tasksView.headerNameColumnWidth";
    private static final String PREF_HEADER_DESCRIPTION_COLUMN_WIDTH = "tasksView.headerDescriptionColumnWidth";
    private static final String PREF_GROUP_TASKS = "tasksView.groupTasks";
    private static final String PREF_FLATTEN_PROJECT_HIERARCHY = "tasksView.flattenProjectHierarchy";

    private boolean projectTasksVisible;
    private boolean taskSelectorsVisible;
    private boolean privateTasksVisible;
    private boolean sortByType;
    private boolean sortByVisibility;
    private boolean linkToSelection;
    private int headerNameColumnWidth;
    private int headerDescriptionColumnWidth;
    private boolean groupTasks;
    private boolean projectHierarchyFlattened;

    public void load() {
        IEclipsePreferences prefs = EclipsePreferencesUtils.getInstanceScope().getNode(UiPlugin.PLUGIN_ID);
        this.projectTasksVisible = prefs.getBoolean(PREF_PROJECT_TASKS_VISIBLE, false);
        this.taskSelectorsVisible = prefs.getBoolean(PREF_TASK_SELECTORS_VISIBLE, true);
        this.privateTasksVisible = prefs.getBoolean(PREF_PRIVATE_TASKS_VISIBLE, false);
        this.sortByType = prefs.getBoolean(PREF_SORT_BY_TYPE, true);
        this.sortByVisibility = prefs.getBoolean(PREF_SORT_BY_VISIBILITY, true);
        this.linkToSelection = prefs.getBoolean(PREF_LINK_TO_SELECTION, false);
        this.headerNameColumnWidth = prefs.getInt(PREF_HEADER_NAME_COLUMN_WIDTH, 200);
        this.headerDescriptionColumnWidth = prefs.getInt(PREF_HEADER_DESCRIPTION_COLUMN_WIDTH, 400);
        this.groupTasks = prefs.getBoolean(PREF_GROUP_TASKS, true);
        this.projectHierarchyFlattened = prefs.getBoolean(PREF_FLATTEN_PROJECT_HIERARCHY, false);
    }

    public void save() {
        IEclipsePreferences prefs = EclipsePreferencesUtils.getInstanceScope().getNode(UiPlugin.PLUGIN_ID);
        prefs.putBoolean(PREF_PROJECT_TASKS_VISIBLE, this.projectTasksVisible);
        prefs.putBoolean(PREF_TASK_SELECTORS_VISIBLE, this.taskSelectorsVisible);
        prefs.putBoolean(PREF_PRIVATE_TASKS_VISIBLE, this.privateTasksVisible);
        prefs.putBoolean(PREF_SORT_BY_TYPE, this.sortByType);
        prefs.putBoolean(PREF_SORT_BY_VISIBILITY, this.sortByVisibility);
        prefs.putBoolean(PREF_LINK_TO_SELECTION, this.linkToSelection);
        // minimum width is 1, so that the columns are resizeable by the user
        prefs.putInt(PREF_HEADER_NAME_COLUMN_WIDTH, Math.max(1, this.headerNameColumnWidth));
        prefs.putInt(PREF_HEADER_DESCRIPTION_COLUMN_WIDTH, Math.max(1, this.headerDescriptionColumnWidth));
        prefs.putBoolean(PREF_GROUP_TASKS, this.groupTasks);
        prefs.putBoolean(PREF_FLATTEN_PROJECT_HIERARCHY, this.projectHierarchyFlattened);

        try {
            prefs.flush();
        } catch (BackingStoreException e) {
            UiPlugin.logger().error("Unable to store task view preferences.", e);
        }
    }

    public boolean isProjectTasksVisible() {
        return this.projectTasksVisible;
    }

    public void setProjectTasksVisible(boolean projectTasksVisible) {
        this.projectTasksVisible = projectTasksVisible;
    }

    public boolean isTaskSelectorsVisible() {
        return this.taskSelectorsVisible;
    }

    public void setTaskSelectorsVisible(boolean taskSelectorsVisible) {
        this.taskSelectorsVisible = taskSelectorsVisible;
    }

    public boolean isPrivateTasksVisible() {
        return this.privateTasksVisible;
    }

    public void setPrivateTasksVisible(boolean privateTasksVisible) {
        this.privateTasksVisible = privateTasksVisible;
    }

    public boolean isSortByType() {
        return this.sortByType;
    }

    public void setSortByType(boolean sortByType) {
        this.sortByType = sortByType;
    }

    public boolean isSortByVisibility() {
        return this.sortByVisibility;
    }

    public void setSortByVisibility(boolean sortByVisibility) {
        this.sortByVisibility = sortByVisibility;
    }

    public boolean isLinkToSelection() {
        return this.linkToSelection;
    }

    public void setLinkToSelection(boolean linkToSelection) {
        this.linkToSelection = linkToSelection;
    }

    public int getHeaderNameColumnWidth() {
        return this.headerNameColumnWidth;
    }

    public void setHeaderNameColumnWidth(int headerNameColumnWidth) {
        this.headerNameColumnWidth = headerNameColumnWidth;
    }

    public int getHeaderDescriptionColumnWidth() {
        return this.headerDescriptionColumnWidth;
    }

    public void setHeaderDescriptionColumnWidth(int headerDescriptionColumnWidth) {
        this.headerDescriptionColumnWidth = headerDescriptionColumnWidth;
    }

    public boolean isGroupTasks() {
        return this.groupTasks;
    }

    public void setGroupTasks(boolean groupTasksByTaskGroup) {
        this.groupTasks = groupTasksByTaskGroup;
    }

    public boolean isProjectHierarchyFlattened() {
        return projectHierarchyFlattened;
    }

    public void setProjectHierarchyFlattened(boolean projectHierarchyFlattened) {
        this.projectHierarchyFlattened = projectHierarchyFlattened;
    }

    public void dispose() {
        save();
    }

}
