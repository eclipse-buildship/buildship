/*******************************************************************************
 * Copyright (c) 2019 Gradle Inc.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.core.internal.workspace;

import java.util.Arrays;

import org.gradle.tooling.model.eclipse.EclipseProject;

import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.buildship.core.internal.util.gradle.CompatEclipseProject;

/**
 * Updater responsible for adjusting the project's raw classpath.
 *
 * Currently it only deletes the lib entries from the classpath if the used Gradle version supports
 * custom classpath configuration (Gradle 3.0+).
 *
 * @author Donat Csikos
 */
final class LibraryFilter {

    public static void update(IJavaProject eclipseProject, EclipseProject modelProject, IProgressMonitor monitor) throws JavaModelException {
        if (supportsClasspathCustomization(modelProject)) {
            IClasspathEntry[] newClasspath = filterLibraries(eclipseProject.getRawClasspath());
            eclipseProject.setRawClasspath(newClasspath, monitor);
        }
    }

    private static IClasspathEntry[] filterLibraries(IClasspathEntry[] classpath) throws JavaModelException {
        return FluentIterable.from(Arrays.asList(classpath)).filter(new Predicate<IClasspathEntry>() {

            @Override
            public boolean apply(IClasspathEntry entry) {
                return entry.getEntryKind() != IClasspathEntry.CPE_LIBRARY;
            }
        }).toArray(IClasspathEntry.class);
    }

    private static boolean supportsClasspathCustomization(EclipseProject modelProject) {
        // classpath customization was introduced in Gradle 3.0 along with classpath containers
        return CompatEclipseProject.supportsClasspathContainers(modelProject);
    }
}
