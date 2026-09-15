/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Donát Csikós (Gradle Inc.) - initial API and implementation and initial documentation
 */

package eclipsebuild

import eclipsebuild.BuildDefinitionPlugin.TargetPlatform
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.internal.os.OperatingSystem

/**
 * Holds configuration-dependent settings for the plug-ins.
 */
class Config {

    private final Project project

    static Config on(Project project) {
        return new Config(project)
    }

    private Config(Project project) {
        this.project = project
    }

    TargetPlatform getTargetPlatform() {
        project.rootProject.eclipseBuild.targetPlatforms[eclipseVersion]
    }

    String getEclipseVersion() {
        // to avoid configuration timing issues we don't cache the values in fields
        project.hasProperty('eclipse.version') ?
                project.property('eclipse.version') :
                project.rootProject.eclipseBuild.defaultEclipseVersion
    }

    // the hierarchy obtainable with the API below:
    //
    //   baseDirectory
    //    |--45
    //    |  |--target-platform
    //    |  |--mavenized-target-platform
    //    |     |--version
    //    |--37
    //       |--...

    Provider<Directory> getBaseDirectory() {
        Provider<Directory> baseDirectory = project.rootProject.eclipseBuild.baseDirectory
        return baseDirectory != null ? baseDirectory : project.rootProject.layout.buildDirectory.dir("tooling")
    }

    File getEclipseSdkDir() {
        project.rootProject.eclipseBuild.eclipseSdkDir
    }

    File getTargetPlatformDir() {
        new File(baseDirectory.get().asFile, eclipseVersion)
    }

    File getNonMavenizedTargetPlatformDir() {
        new File(targetPlatformDir, 'target-platform')
    }

    File getMavenizedTargetPlatformDir() {
        new File(targetPlatformDir, 'mavenized-target-platform')
    }

    File getEclipseSdkExe() {
        new File(eclipseSdkDir, Constants.eclipseExePath)
    }

    File getJarProcessorJar() {
        String pluginsDir = OperatingSystem.current().isMacOsX() ? 'Eclipse.app/Contents/Eclipse/plugins' : '/eclipse/plugins'
        new File(eclipseSdkDir.path, pluginsDir).listFiles().find { it.name.startsWith('org.eclipse.equinox.p2.jarprocessor_') }
    }
}
