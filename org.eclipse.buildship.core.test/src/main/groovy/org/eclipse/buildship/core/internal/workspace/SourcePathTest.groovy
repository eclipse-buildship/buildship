package org.eclipse.buildship.core.internal.workspace

import org.eclipse.core.resources.IProject
import org.eclipse.debug.core.ILaunchConfiguration
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.launching.IRuntimeClasspathEntry
import org.eclipse.jdt.launching.JavaRuntime

import org.eclipse.buildship.core.internal.launch.SupportedLaunchConfigType
import org.eclipse.buildship.core.internal.test.fixtures.ProjectSynchronizationSpecification
import org.eclipse.buildship.core.*

class SourcePathTest extends ProjectSynchronizationSpecification {

    def "Source files available from project dependency"(String version) {
         setup:
         File projectDir = dir('root') {
             file('settings.gradle') << 'include "p1","p2"'
             file('build.gradle') << 'allprojects { apply plugin: "java" }'

             dir('p1') {
                 file('build.gradle') << 'dependencies { compile project(":p2") }'
                 dir('src/main/java').mkdirs()
             }

             dir('p2') {
                 file('build.gradle')
                 dir('src/main/java').mkdirs()
             }
         }

         when:
         importAndWait(projectDir, GradleDistributions.forVersion(version))
         IRuntimeClasspathEntry[] p1sources = sourceEntries(findProject('p1'))
         IRuntimeClasspathEntry[] p2sources = sourceEntries(findProject('p2'))

         then:
         p1sources.find { IRuntimeClasspathEntry entry -> entry.path.toPortableString() == '/p1' }
         p1sources.find { IRuntimeClasspathEntry entry -> entry.path.toPortableString() == '/p2' }
         !p2sources.find { IRuntimeClasspathEntry entry -> entry.path.toPortableString() == '/p1' }
         p2sources.find { IRuntimeClasspathEntry entry -> entry.path.toPortableString() == '/p2' }

         where:
         // Gradle 4.3 doesn't use separate output dir per source folder
         version << ['4.3', '4.4']
    }

    private IRuntimeClasspathEntry[] sourceEntries(IProject project) {
        ILaunchConfiguration launchConfig = createLaunchConfig(SupportedLaunchConfigType.JDT_JAVA_APPLICATION.id)
        IRuntimeClasspathEntry[] unresolved = JavaRuntime.computeUnresolvedRuntimeClasspath(JavaCore.create(project))
        JavaRuntime.resolveSourceLookupPath(unresolved, launchConfig)
    }
}
