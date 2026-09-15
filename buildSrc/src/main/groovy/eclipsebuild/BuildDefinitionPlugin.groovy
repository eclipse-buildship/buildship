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

import eclipsebuild.jar.ExistingJarBundlePlugin
import eclipsebuild.mavenize.BundleMavenDeployer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.Directory
import org.gradle.api.logging.LogLevel
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.ExecOperations

import javax.inject.Inject

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
 * in the {@link Config} class. Target platforms live under the root project's build directory, so
 * {@code gradle clean} removes them and each checkout keeps its own copy.
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
    static final String TASK_NAME_PREPARE_ECLIPSE_SDK = "prepareEclipseSdk"
    static final String TASK_NAME_VALIDATE_ECLIPSE_SDK = "validateEclipseSdk"
    static final String TASK_NAME_ASSEMBLE_TARGET_PLATFORM = "assembleTargetPlatform"
    static final String TASK_NAME_ADD_EXISTING_JAR_BUNDLES_TO_TARGET_PLATFORM = "addExistingJarBundlesToTargetPlatform"
    static final String TASK_NAME_INSTALL_TARGET_PLATFORM = "installTargetPlatform"

    // the Eclipse SDK that provides the p2 director used to assemble and modify target platforms
    static final String ECLIPSE_SDK_VERSION = "4.27"

    static final Attribute artifactType = Attribute.of('artifactType', String)

    @Override
    void apply(Project project) {
        configureProject(project)

        Config config = Config.on(project)
        createEclipseSdkDependencies(project)
        validateDslBeforeBuildStarts(project, config)
        addTaskPrepareEclipseSdk(project, config)
        validateEclipseDownLoad(project, config)
        addTaskAssembleTargetPlatform(project, config)
        addTaskAddExistingJarsToTargetPlatform(project, config)
        addTaskInstallTargetPlatform(project, config)
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
            eclipseSdks(group: 'org.eclipse', name: 'eclipse-sdk', version: ECLIPSE_SDK_VERSION) {
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

    static void addTaskPrepareEclipseSdk(Project project, Config config) {
        project.task(TASK_NAME_PREPARE_ECLIPSE_SDK) {
            description = "Copies the unpacked Eclipse SDK out of the immutable artifact transform cache into a writable location."
            def sdkFiles = project.configurations.eclipseSdks.incoming.artifactView {
                attributes.attribute(artifactType, ARTIFACT_TYPE_NAME)
            }.files
            inputs.dir sdkFiles.singleFile

            doLast {
                File source = sdkFiles.singleFile
                File dest = eclipseSdkCopyDir(project)
                File exe = new File(dest, Constants.eclipseExePath)
                if (!exe.exists()) {
                    project.logger.info("Copy Eclipse SDK from '${source}' to '${dest}'")
                    project.copy {
                        from source
                        into dest
                    }
                    exe.setExecutable(true)
                }
            }
        }
    }

    static File eclipseSdkCopyDir(Project project) {
        new File(project.rootProject.buildDir, "tooling/eclipse-sdk")
    }

    def static validateEclipseDownLoad(Project project, Config config) {
        project.task(TASK_NAME_VALIDATE_ECLIPSE_SDK, dependsOn: TASK_NAME_PREPARE_ECLIPSE_SDK) {
            description = "Validates the Eclipse SDK download."

            doLast {
                def sdk = eclipseSdkCopyDir(project)
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
                getTargetPlatformBaseDir().set(config.targetPlatformBaseDir)
            }

            eclipseSdkVersion.convention(ECLIPSE_SDK_VERSION)
            eclipseSdkClassifier.convention(eclipseSdkDownloadClassifier)
            os.convention(Constants.os)
            ws.convention(Constants.ws)
            arch.convention(Constants.arch)
            eclipseSdkExe.convention(project.provider { Config.on(project).eclipseSdkExe.path })
            repositoryMirrorUrls.convention(project.hasProperty('repository.mirrors') ? project.property('repository.mirrors') as String : null)
            projectLocation.convention(project.projectDir.absolutePath)
        }
    }

    /**
     * Copies the assembled distribution and installs the locally built jar bundles into the copy.
     * <p/>
     * Working on a copy is what keeps {@code assembleTargetPlatform} cacheable. Its output has to stay exactly as the
     * p2 director produced it, and these bundles are rebuilt whenever the wrapped library changes.
     */
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
                        inputs.dir(new File(p.buildDir, ExistingJarBundlePlugin.P2_REPOSITORY_FOLDER))
                                .withPropertyName("${p.name}P2Repository")
                                .withPathSensitivity(PathSensitivity.RELATIVE)
                    }
                }
            }

            project.afterEvaluate {
                inputs.dir(config.targetPlatformBaseDir).withPropertyName('targetPlatformBase')
                outputs.dir(config.nonMavenizedTargetPlatformDir).withPropertyName('targetPlatform')
            }

            doLast {
                def execOps = project.objects.newInstance(InjectedExecOps)
                copyAssembledTargetPlatform(project, config)
                addExistingJarsToTargetPlatform(project, config, execOps)
            }
        }
    }

    static void copyAssembledTargetPlatform(Project project, Config config) {
        project.logger.info("Copy the assembled target platform '${config.targetPlatformBaseDir}' to '${config.nonMavenizedTargetPlatformDir}'")
        project.sync {
            from config.targetPlatformBaseDir
            into config.nonMavenizedTargetPlatformDir
        }
        retargetEclipseIni(config)
    }

    /**
     * Points the copied distribution's launcher configuration at the copy rather than at the assembled original.
     * <p/>
     * The p2 director writes the launcher paths in eclipse.ini relative to the install directory, so a plain copy
     * still refers to the directory it was assembled in. The p2 director refuses to install into such a copy, failing
     * with "Error while loading manipulator".
     */
    static void retargetEclipseIni(Config config) {
        File eclipseIni = new File(config.nonMavenizedTargetPlatformDir, 'eclipse.ini')
        if (eclipseIni.exists()) {
            eclipseIni.text = eclipseIni.text.replace(
                    "../${config.targetPlatformBaseDir.name}/",
                    "../${config.nonMavenizedTargetPlatformDir.name}/")
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
                    *Constants.p2VmArgs)

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
                    *Constants.p2VmArgs)
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

}
