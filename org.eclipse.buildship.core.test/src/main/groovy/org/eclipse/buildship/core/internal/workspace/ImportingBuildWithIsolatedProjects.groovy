/*******************************************************************************
 * Copyright (c) 2026 Gradle Inc. and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.core.internal.workspace

import static org.gradle.api.JavaVersion.VERSION_17

import org.gradle.api.JavaVersion
import spock.lang.IgnoreIf

import org.eclipse.buildship.core.GradleDistribution
import org.eclipse.buildship.core.SynchronizationResult
import org.eclipse.buildship.core.internal.test.fixtures.ProjectSynchronizationSpecification

class ImportingBuildWithIsolatedProjects extends ProjectSynchronizationSpecification {

    // Buildship injects an init script into every build to apply the eclipse plugin. It has to
    // reach all projects without letting one project configure another, otherwise isolated projects
    // rejects the build before any model can be queried.
    //
    // The synchronization does not fully succeed against a released Gradle. Gradle's own
    // RunEclipseTasksBuilder reads Project.extensions of every project through allprojects, so the
    // assertion is limited to the violation that Buildship is responsible for. Against a Gradle
    // build that also fixes the model builders the whole import succeeds, so this can assert a
    // clean import once such a version is released.
    @IgnoreIf({ !JavaVersion.current().isCompatibleWith(VERSION_17) }) // Gradle 9.7.1 requires Java 17 or above
    def "The injected init script does not configure one project from another"() {
        setup:
        File rootProject = dir('isolated-projects') {
            dir 'sub'
            file 'settings.gradle', "include 'sub'"
            file 'gradle.properties', 'org.gradle.unsafe.isolated-projects=true'
        }

        when:
        SynchronizationResult result = tryImportAndWait(rootProject, GradleDistribution.forVersion('9.7.1'))

        then:
        !stackTraceOf(result).contains("'Project.apply' functionality on subprojects")
    }

    private static String stackTraceOf(SynchronizationResult result) {
        Throwable exception = result.status.exception
        if (exception == null) {
            return ''
        }
        StringWriter stackTrace = new StringWriter()
        exception.printStackTrace(new PrintWriter(stackTrace))
        stackTrace.toString()
    }
}
