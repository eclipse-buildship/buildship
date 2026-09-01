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

import org.gradle.api.Project
import org.gradle.internal.os.OperatingSystem

/**
 * Contains all constants which are independent from the configuration.
 */
class Constants {

    /**
     * Returns the group name of all tasks that contribute to the Eclipse Plugin build.
     *
     * @return the name of the group where all tasks defined in this project should show upon the execution of <code>gradle tasks</code>
     */
    static String getGradleTaskGroupName() {
        return "Eclipse Plugin Build"
    }

    /**
     * Returns the Eclipse runtime abbreviation of the operating system.
     *
     * http://help.eclipse.org/indigo/topic/org.eclipse.platform.doc.isv/reference/misc/runtime-options.html
     *
     * @return the operating system: 'linux', 'win32', 'macosx', or null
     */
    static String getOs() {
        OperatingSystem os = OperatingSystem.current()
        os.isLinux() ? 'linux' : os.isWindows() ? 'win32' : os.isMacOsX() ? 'macosx': null
    }

    /**
     * Return the Eclipse runtime abbreviation of the windowing system.
     *
     * http://help.eclipse.org/indigo/topic/org.eclipse.platform.doc.isv/reference/misc/runtime-options.html
     *
     * @return the windowing system: 'gtk', 'win32', 'cocoa', or null
     */
    static String getWs() {
        OperatingSystem os = OperatingSystem.current()
        os.isLinux() ? 'gtk' : os.isWindows() ? 'win32' : os.isMacOsX() ? 'cocoa' : null
    }

    /**
     * Returns the Eclipse runtime abbreviation of the architecture.
     *
     * http://help.eclipse.org/indigo/topic/org.eclipse.platform.doc.isv/reference/misc/runtime-options.html
     *
     * @return the architecture: x86_64 or x86
     */
    static String getArch() {
        def arch = System.getProperty("os.arch")
        if (arch == 'aarch64') {
            return arch
        }
        return arch.contains("64") ? "x86_64" : "x86"
    }

    /**
     * Returns the eclipse-sdk dependency type specific for the current OS.
     *
     * @return the dependency type: 'tar.gz', 'zip', 'dmg', or null
     */
    static String getType() {
        def os = OperatingSystem.current()
        os.isLinux() ? 'tar.gz' : os.isWindows() ? 'zip' : os.isMacOsX() ? 'dmg' : null
    }


    /**
     * Returns the group ID of the mavenized Eclipse plugins.
     *
     * @return the group ID referencing Eclipse plugins of the mavenized target platform
     */
    static String getMavenizedEclipsePluginGroupName() {
        return "eclipse"
    }

    /**
     * Returns the OS-specific part of the path of the Eclipse executable.
     *
     * @return the OS-specific part of the path of the Eclipse executable, or null
     */
    static String getEclipseExePath() {
        OperatingSystem os = OperatingSystem.current()
        os.isLinux() ? "eclipse/eclipse" :
                os.isWindows() ? "eclipse/eclipse.exe" :
                os.isMacOsX() ? "Eclipse.app/Contents/MacOS/eclipse" :
                null
    }

    /**
     * Returns the URL from where the Eclipse SDK (4.4.2) can be downloaded. The URL always redirects to a mirror from
     * where the Eclipse SDK can be downloaded.
     *
     * @return the URL from where the Eclipse SDK can be downloaded
     */

    static String getEclipseSdkDownloadClassifier() {
        def os = getOs()
        if(os == 'win32') {
            return 'win32-x86_64'
        }
        return "$os-$ws-$arch"
    }

    static String getEclipseSdkDownloadSha256Hash() {
        def classifier = "$os.$arch"
        switch (classifier) {
            case "win32.x86_64":   return '7ea771854d990556a8c0568fe20f115d7b15432fd14eec85cff7db589581b38b'
            case "linux.aarch64" : return 'ec6c0a18d1d63d9dee385cb3423d5d7ed145cffde86b68fc5d69119f4c279656'
            case "linux.x86_64":   return 'dfd0d753bc08b1dc6452934a9d83d3efea8f2760eb17b1bb509a8f7e1103186b'
            case "macosx.x86_64":  return '7b7d1315528331141a024737e7af9e2d1b12505be813de14776cce7c88ae8832'
            case "macosx.aarch64": return '22409543fed3086203445aa285f2658639f2b8b715e54cb57d00fc4c618bbff2'
            default: throw new RuntimeException("Unsupported platform (SHA256): $classifier")
        }
    }

    /**
     * Returns the arguments to pass to the JVM that runs a p2 application in the Eclipse SDK.
     * <p>
     * JDK 26 is the first JDK that ships a populated {@code conf/jaxp.properties}, which caps the
     * size of a parsed XML document at 100 kB. The content.xml of an Eclipse release train is far
     * beyond that - the 2026-06 one is about 28 MB - so without lifting the two size limits the p2
     * director cannot read the repository at all and fails with JAXP00010003 / JAXP00010004.
     *
     * @return the arguments to append to the Eclipse SDK command line, starting with -vmargs
     */
    static List<String> getP2VmArgs() {
        ['-vmargs',
         '-Declipse.p2.mirror=false',
         '-Djdk.xml.maxGeneralEntitySizeLimit=0',
         '-Djdk.xml.totalEntitySizeLimit=0']
    }

    /**
     * Sets some constants in the target project's build script.
     */
    static exposePublicConstantsFor(Project project) {
        project.ext.ECLIPSE_OS = os
        project.ext.ECLIPSE_WS = ws
        project.ext.ECLIPSE_ARCH = arch
    }

}
