/*******************************************************************************
 * Copyright (c) 2023 Gradle Inc. and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.core.internal.workspace

import org.eclipse.core.resources.IProject
import org.eclipse.debug.core.ILaunchConfiguration
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.launching.IRuntimeClasspathEntry
import org.eclipse.jdt.launching.JavaRuntime

import org.eclipse.buildship.core.internal.launch.SupportedLaunchConfigType
import org.eclipse.buildship.core.internal.test.fixtures.ProjectSynchronizationSpecification
import org.eclipse.buildship.core.GradleDistribution

class SourcePathTest extends ProjectSynchronizationSpecification {

    def "Source files available from project dependency"() {
         setup:
         File projectDir = dir('root') {
             file('settings.gradle') << 'include "p1","p2"'
             file('build.gradle') << 'allprojects { apply plugin: "java" }'

             dir('p1') {
                 file('build.gradle') << 'dependencies { implementation project(":p2") }'
                 dir('src/main/java').mkdirs()
             }

             dir('p2') {
                 file('build.gradle')
                 dir('src/main/java').mkdirs()
             }
         }

         when:
         importAndWait(projectDir, GradleDistribution.fromBuild())
         IRuntimeClasspathEntry[] p1sources = sourceEntries(findProject('p1'))
         IRuntimeClasspathEntry[] p2sources = sourceEntries(findProject('p2'))

         then:
         p1sources.find { IRuntimeClasspathEntry entry -> entry.path.toPortableString() == '/p1' }
         p1sources.find { IRuntimeClasspathEntry entry -> entry.path.toPortableString() == '/p2' }
         !p2sources.find { IRuntimeClasspathEntry entry -> entry.path.toPortableString() == '/p1' }
         p2sources.find { IRuntimeClasspathEntry entry -> entry.path.toPortableString() == '/p2' }
    }

    private IRuntimeClasspathEntry[] sourceEntries(IProject project) {
        ILaunchConfiguration launchConfig = createLaunchConfig(SupportedLaunchConfigType.JDT_JAVA_APPLICATION.id)
        IRuntimeClasspathEntry[] unresolved = JavaRuntime.computeUnresolvedRuntimeClasspath(JavaCore.create(project))
        JavaRuntime.resolveSourceLookupPath(unresolved, launchConfig)
    }
}
