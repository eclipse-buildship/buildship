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

import groovy.xml.XmlSlurper

import com.google.common.hash.HashFunction
import com.google.common.hash.Hashing
import eclipsebuild.jar.ExistingJarBundlePlugin
import eclipsebuild.mavenize.BundleMavenDeployer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.Directory
import org.gradle.api.logging.LogLevel
import org.gradle.api.provider.Provider
import org.gradle.process.ExecOperations

import javax.inject.Inject
import java.nio.charset.StandardCharsets

import static eclipsebuild.Constants.eclipseSdkDownloadClassifier
import static eclipsebuild.UnPack.ARTIFACT_TYPE_NAME

/**
 * Gradle plugin for the root project of the Eclipse plugin build.
 * <p/>
 * Applying this plugin offers a DSL to specify Eclipse target platforms which will be the base
 * of the compilation of the sub-projects applying applying the following plug-ins:
 * {@link BundlePlugin}, {@link TestBundlePlugin}, {@link FeaturePlugin}, {@link UpdateSitePlugin}.
 * <p/>
 * A target platform references a standard target definition file to define the composing features.
 * When building, the platform is assembled by using the P2 director application.
 * <p/>
 * A valid target platform definition DSL looks like this:
 * <pre>
 * eclipseBuild {
 *     defaultEclipseVersion = '44'
 *
 *     targetPlatform {
 *         eclipseVersion = '44'
 *         targetDefinition = file('tooling-e44.target')
 *         versionMapping = [
 *             'org.eclipse.core.runtime' : '3.10.0.v20140318-2214'
 *         ]
 *     }
 *
 *     targetPlatform {
 *        eclipseVersion = '43'
 *        ...
 *     }
 * }
 * </pre>
 * If no target platform version is defined for the build then the one matches to the value of the
 * {@link defaultEclipseVersion} attribute will be selected. This can be changed by appending the
 * the {@code -Peclipse.version=[version-number]} argument to he build. In the context of the
 * example above it would be:
 * <pre>
 * gradle clean build -Peclipse.version=43
 * </pre>
 * The directory layout where the target platform and it's mavenized counterpart stored is defined
 * in the {@link Config} class. The directory containing the target platforms can be redefined with
 * the {@code -PtargetPlatformsDir=<path>} argument.
 * <p/>
 * The {@code versionMapping} can be used to define exact plugin dependency versions per target platform.
 * A bundle can define a dependency through the {@code withEclipseBundle()} method like
 * <pre>
 * api withEclipseBundle('org.eclipse.core.runtime')
 * </pre>
 * If the active target platform has a version mapped for the dependency then that version is used,
 * otherwise an unbound version range (+) is applied.
 */

class BuildDefinitionPlugin implements Plugin<Project> {

    /**
     *  Extension class providing top-level content of the DSL definition for the plug-in.
     */

    static class EclipseBuild {

        def defaultEclipseVersion
        final def targetPlatforms
        def scmRepo
        def commitId
        Provider<Directory> baseDirectory
        File eclipseSdkDir

        EclipseBuild() {
            targetPlatforms = [:]
        }

        def targetPlatform(Closure closure) {
            def tp = new TargetPlatform()
            tp.apply(closure)
            targetPlatforms[tp.eclipseVersion] = tp
        }
    }

    /**
     * POJO class describing one target platform. Instances are stored in the {@link EclipseBuild#targetPlatforms} map.
     */
    static class TargetPlatform {

        def eclipseVersion
        def targetDefinition
        def versionMapping

        TargetPlatform() {
            this.versionMapping = [:]
        }

        def apply(Closure closure) {
            closure.resolveStrategy = Closure.DELEGATE_FIRST
            closure.delegate = this
            closure.call()
            // convert GStrings to Strings in the versionMapping key to avoid lookup misses
            versionMapping = versionMapping.collectEntries { k, v -> [k.toString(), v] }
        }
    }

    interface InjectedExecOps {
        @Inject
        ExecOperations getExecOps()
    }

    // name of the root node in the DSL
    static String DSL_EXTENSION_NAME = "eclipseBuild"

    // task names
    static final String TASK_NAME_DOWNLOAD_ECLIPSE_SDK = "downloadEclipseSdk"
    static final String TASK_NAME_VALIDATE_ECLIPSE_SDK = "validateEclipseSdk"
    static final String TASK_NAME_ASSEMBLE_TARGET_PLATFORM = "assembleTargetPlatform"
    static final String TASK_NAME_ADD_EXISTING_JAR_BUNDLES_TO_TARGET_PLATFORM = "addExistingJarBundlesToTargetPlatform"
    static final String TASK_NAME_INSTALL_TARGET_PLATFORM = "installTargetPlatform"
    static final String TASK_NAME_UNINSTALL_TARGET_PLATFORM = "uninstallTargetPlatform"
    static final String TASK_NAME_UNINSTALL_ALL_TARGET_PLATFORMS = "uninstallAllTargetPlatforms"

    static final Attribute artifactType = Attribute.of('artifactType', String)

    @Override
    void apply(Project project) {
        configureProject(project)

        Config config = Config.on(project)
        createEclipseSdkDependencies(project)
        validateDslBeforeBuildStarts(project, config)
        validateEclipseDownLoad(project, config)
        addTaskAssembleTargetPlatform(project, config)
        addTaskAddExistingJarsToTargetPlatform(project, config)
        addTaskInstallTargetPlatform(project, config)
        addTaskUninstallTargetPlatform(project, config)
        addTaskUninstallAllTargetPlatforms(project, config)
    }

    private static createEclipseSdkDependencies(Project project) {
        project.repositories {
            maven {
                name = "Gradle public repository"
                url = "https://repo.gradle.org/artifactory/ext-releases-local"
                metadataSources {
                    artifact()
                }
            }
        }
        project.configurations {
            eclipseSdks
        }
        project.dependencies {
            eclipseSdks(group: 'org.eclipse', name: 'eclipse-sdk', version: '4.27') {
                artifact {
                    type = Constants.type
                    classifier = eclipseSdkDownloadClassifier
                }
            }

            registerTransform(UnZip) {
                from.attribute(artifactType, "zip")
                to.attribute(artifactType, ARTIFACT_TYPE_NAME)
            }
            registerTransform(UnTarGz) {
                from.attribute(artifactType, "tar.gz")
                to.attribute(artifactType, ARTIFACT_TYPE_NAME)
            }

            registerTransform(UnDmg) {
                from.attribute(artifactType, "dmg")
                to.attribute(artifactType, ARTIFACT_TYPE_NAME)
            }
        }
    }

    static void configureProject(Project project) {
        // add extension
        project.extensions.create(DSL_EXTENSION_NAME, EclipseBuild)

        // expose some constants to the build files, e.g. for platform-dependent dependencies
        Constants.exposePublicConstantsFor(project)

        // make the withEclipseBundle(String) method available in the build script
        project.ext.withEclipseBundle = { String pluginName -> DependencyUtils.calculatePluginDependency(project, pluginName) }
    }

    static void validateDslBeforeBuildStarts(Project project, Config config) {
        // check if the build definition is valid just before the build starts
        project.gradle.taskGraph.whenReady {
            if (project.eclipseBuild.defaultEclipseVersion == null) {
                throw new RuntimeException("$DSL_EXTENSION_NAME must specify 'defaultEclipseVersion'.")
            }

            // check if the selected target platform exists for the given Eclipse version
            def targetPlatform = config.targetPlatform
            if (targetPlatform == null) {
                throw new RuntimeException("No target platform is defined for selected Eclipse version '${config.eclipseVersion}'.")
            }

            // check if a target platform file is referenced
            def targetDefinition = targetPlatform.targetDefinition
            if (targetDefinition == null || !targetDefinition.exists()) {
                throw new RuntimeException("No target definition file found for '${targetDefinition}'.")
            }

            // check if target definition file is a valid XML
            try {
                new XmlSlurper().parseText(targetDefinition.text)
            } catch (Exception e) {
                throw new RuntimeException("Target definition file '$targetDefinition' must be a valid XML document.", e)
            }
        }
    }

    def static validateEclipseDownLoad(Project project, Config config) {
        project.task(TASK_NAME_VALIDATE_ECLIPSE_SDK) {
            description = "Validates the Eclipse SDK download."
            def sdkFiles = project.configurations.eclipseSdks.incoming.artifactView {
                attributes.attribute(artifactType, ARTIFACT_TYPE_NAME)
            }.files
            inputs.dir sdkFiles.singleFile

            doLast {
                def sdk = sdkFiles.singleFile
                if (sdk == null) {
                    throw new RuntimeException("Eclipse SDK download failed. Please check the log for details.")
                }
                if (!sdk.exists()) {
                    throw new RuntimeException("Eclipse SDK download failed. File '${sdk}' does not exist.")
                }
                project.rootProject.eclipseBuild.eclipseSdkDir = sdk
            }
        }
    }

    static void addTaskAssembleTargetPlatform(Project project, Config config) {
        project.tasks.create(TASK_NAME_ASSEMBLE_TARGET_PLATFORM, AssembleTargetPlatformTask) {
            dependsOn(TASK_NAME_VALIDATE_ECLIPSE_SDK)
            group = Constants.gradleTaskGroupName
            description = "Assembles an Eclipse distribution based on the target platform definition."

            project.afterEvaluate {
                getTargetPlatformFile().set(config.targetPlatform.targetDefinition as File)
                getNonMavenizedTargetPlatformDir().set(config.nonMavenizedTargetPlatformDir)
            }

            eclipseSdkExe.convention(project.provider { Config.on(project).eclipseSdkExe.path })
            repositoryMirrorUrls.convention(project.hasProperty('repository.mirrors') ? project.property('repository.mirrors') as String : null)

            onlyIf {
                String hash = targetPlatformHash(project, config.targetPlatform.targetDefinition.text)
                File digestFile = new File(config.nonMavenizedTargetPlatformDir, 'digest')

                if (!digestFile.exists()) {
                    project.logger.info("No digest file found in '${config.nonMavenizedTargetPlatformDir}'; reassemble the target platform.")
                    return true
                } else {
                    boolean digestMatch = digestFile.text == hash
                    if (!digestMatch) {
                        project.logger.info("Target definition file or the manifest file of an existing jur bundle plugin has changed; reassemble the target platform.")
                    }
                    return !digestMatch
                }
            }
        }
    }

    static String targetPlatformHash(Project project, String targetDefinitionText) {
        HashFunction sha512HashFunction = Hashing.sha512()
        String manifests = project.rootProject.allprojects
                .findAll { p -> p.plugins.hasPlugin(ExistingJarBundlePlugin) }
                .toSorted { p1, p2 -> p1.name <=> p2.name }
                .collect { p -> p.file("META-INF/MANIFEST.MF").exists() ? p.file("META-INF/MANIFEST.MF").text : null }
                .findAll { it != null }
                .join("\n")
        String hashInput = manifests + "\n" + targetDefinitionText
        return sha512HashFunction.hashString(hashInput, StandardCharsets.UTF_8)
    }

    static void addTaskAddExistingJarsToTargetPlatform(Project project, Config config) {
        project.task(TASK_NAME_ADD_EXISTING_JAR_BUNDLES_TO_TARGET_PLATFORM, dependsOn: [
                TASK_NAME_ASSEMBLE_TARGET_PLATFORM,
        ]) {
            group = Constants.gradleTaskGroupName
            description = "Adds local jar bundle plugins to the assembled target platform"


            // install existing jar bundles
            project.rootProject.allprojects.each { Project p ->
                p.afterEvaluate {
                    if (p.plugins.hasPlugin(ExistingJarBundlePlugin)) {
                        dependsOn p.tasks[ExistingJarBundlePlugin.TASK_NAME_CREATE_P2_REPOSITORY]
                    }
                }
            }

            doLast {
                def execOps = project.objects.newInstance(InjectedExecOps)
                addExistingJarsToTargetPlatform(project, config, execOps)
            }

            onlyIf {
                Task t = project.tasks[TASK_NAME_ASSEMBLE_TARGET_PLATFORM]
                boolean didWork = t.state.didWork
                project.rootProject.allprojects.each { Project p ->
                    if (p.plugins.hasPlugin(ExistingJarBundlePlugin)) {
                        didWork = didWork || p.tasks[ExistingJarBundlePlugin.TASK_NAME_CREATE_P2_REPOSITORY].state.didWork
                    }
                }
                didWork
            }
        }
    }

    static void addExistingJarsToTargetPlatform(Project project, Config config, InjectedExecOps execOps) {
        project.rootProject.allprojects.each { Project p ->
            if (p.plugins.hasPlugin(ExistingJarBundlePlugin)) {
                String repo = new File(p.buildDir, ExistingJarBundlePlugin.P2_REPOSITORY_FOLDER).toURI().toURL().toString()
                executeP2Director(p, config, repo, p.extensions.bundleInfo.bundleName.get(), execOps)
            }
        }
    }

    private static void executeP2Director(Project project, Config config, String repositoryUrl, String installIU, InjectedExecOps execOps) {
        execOps.execOps.exec {

            // redirect the external process output to the logging
            it.standardOutput = new LogOutputStream(project.getLogger(), LogLevel.INFO, LogOutputStream.Type.STDOUT)
            it.errorOutput = new LogOutputStream(project.getLogger(), LogLevel.INFO, LogOutputStream.Type.STDERR)

            it.commandLine(config.eclipseSdkExe.path,
                    '-application', 'org.eclipse.equinox.p2.director',
                    '-repository', repositoryUrl,
                    '-uninstallIU', installIU,
                    '-tag', 'target-platform',
                    '-destination', config.nonMavenizedTargetPlatformDir.path,
                    '-profile', 'SDKProfile',
                    '-bundlepool', config.nonMavenizedTargetPlatformDir.path,
                    '-p2.os', Constants.os,
                    '-p2.ws', Constants.ws,
                    '-p2.arch', Constants.arch,
                    '-roaming',
                    '-nosplash',
                    '-consoleLog',
                    '-vmargs', '-Declipse.p2.mirror=false')

            it.ignoreExitValue = true
        }

        execOps.execOps.exec {

            // redirect the external process output to the logging
            it.standardOutput = new LogOutputStream(project.getLogger(), LogLevel.INFO, LogOutputStream.Type.STDOUT)
            it.errorOutput = new LogOutputStream(project.getLogger(), LogLevel.INFO, LogOutputStream.Type.STDERR)

            it.commandLine(config.eclipseSdkExe.path,
                    '-application', 'org.eclipse.equinox.p2.director',
                    '-repository', repositoryUrl,
                    '-installIU', installIU,
                    '-tag', 'target-platform',
                    '-destination', config.nonMavenizedTargetPlatformDir.path,
                    '-profile', 'SDKProfile',
                    '-bundlepool', config.nonMavenizedTargetPlatformDir.path,
                    '-p2.os', Constants.os,
                    '-p2.ws', Constants.ws,
                    '-p2.arch', Constants.arch,
                    '-roaming',
                    '-nosplash',
                    '-consoleLog',
                    '-vmargs', '-Declipse.p2.mirror=false')
        }
    }

    static void addTaskInstallTargetPlatform(Project project, Config config) {
        project.task(TASK_NAME_INSTALL_TARGET_PLATFORM, dependsOn: TASK_NAME_ADD_EXISTING_JAR_BUNDLES_TO_TARGET_PLATFORM) {
            group = Constants.gradleTaskGroupName
            description = "Converts the assembled Eclipse distribution to a Maven repository."
            project.afterEvaluate { inputs.dir config.nonMavenizedTargetPlatformDir }
            project.afterEvaluate { outputs.dir config.mavenizedTargetPlatformDir }
            doLast { installTargetPlatform(project, config, it) }
        }
    }

    static void installTargetPlatform(Project project, Config config, Task task) {
        // delete the mavenized target platform directory to ensure that the deployment doesn't
        // have outdated artifacts
        if (config.mavenizedTargetPlatformDir.exists()) {
            project.logger.info("Delete mavenized platform directory '${config.mavenizedTargetPlatformDir}'")
            config.mavenizedTargetPlatformDir.deleteDir()
        }

        // install bundles
        project.logger.info("Convert Eclipse target platform '${config.nonMavenizedTargetPlatformDir}' to Maven repository '${config.mavenizedTargetPlatformDir}'")
        def deployer = new BundleMavenDeployer(task.ant, Constants.mavenizedEclipsePluginGroupName, project.logger)
        deployer.deploy(config.nonMavenizedTargetPlatformDir, config.mavenizedTargetPlatformDir)
    }

    static void addTaskUninstallTargetPlatform(Project project, Config config) {
        project.task(TASK_NAME_UNINSTALL_TARGET_PLATFORM) {
            group = Constants.gradleTaskGroupName
            description = "Deletes the target platform."
            doLast { deleteFolder(project, config.targetPlatformDir) }
        }
    }

    static void deleteFolder(Project project, File folder) {
        if (!folder.exists()) {
            project.logger.info("'$folder' doesn't exist")
        } else {
            project.logger.info("Delete '$folder'")
            def success = folder.deleteDir()
            if (!success) {
                throw new RuntimeException("Failed to delete '$folder'")
            }
        }
    }

    static void addTaskUninstallAllTargetPlatforms(Project project, Config config) {
        project.task(TASK_NAME_UNINSTALL_ALL_TARGET_PLATFORMS) {
            group = Constants.gradleTaskGroupName
            description = "Deletes all target platforms from the current machine."
            doLast { deleteFolder(project, config.targetPlatformsDir) }
        }
    }
}
