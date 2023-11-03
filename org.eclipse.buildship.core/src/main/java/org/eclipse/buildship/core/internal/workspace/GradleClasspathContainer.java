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

import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.ClasspathContainerInitializer;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.buildship.core.internal.GradlePluginsRuntimeException;

/**
 * API to define classpath container for Buildship project and external dependencies.
 */
abstract class GradleClasspathContainer implements IClasspathContainer {

    /**
     * The path where all Gradle projects store their external
     * dependencies. This path is added during the project import and the
     * {@code org.eclipse.jdt.core.classpathContainerInitializer} extension populates it with the
     * actual external (source and binary) jars.
     */
    public static final Path CONTAINER_PATH = new Path("org.eclipse.buildship.core.gradleclasspathcontainer");
    /**
     * Creates a new classpath container instance.
     *
     * @param classpathEntries the list of dependencies the container holds
     * @return the classpath container references
     */
    public static IClasspathContainer newInstance(List<IClasspathEntry> classpathEntries) {
        return new DefaultGradleClasspathContainer(CONTAINER_PATH, classpathEntries);
    }

    /**
     * Updates the content of the Gradle classpath container asynchronously on the target project.
     * <p/>
     * This method finds the Gradle classpath container on the project and requests the content
     * update.
     *
     * @throws GradlePluginsRuntimeException if the classpath container update request fails
     * @param project the target project
     */
    public static void requestUpdateOf(IJavaProject project) {
        ClasspathContainerInitializer initializer = JavaCore.getClasspathContainerInitializer(CONTAINER_PATH.toString());
        try {
            initializer.requestClasspathContainerUpdate(CONTAINER_PATH, project, null);
        } catch (CoreException e) {
            throw new GradlePluginsRuntimeException(e);
        }
    }

}
