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

import eclipsebuild.testing.EclipseTestExecuter
import eclipsebuild.testing.EclipseTestExtension
import eclipsebuild.testing.EclipseTestTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.tasks.testing.Test
import org.gradle.internal.operations.BuildOperationExecutor

import javax.inject.Inject

/**
 * Gradle plug-in to build Eclipse test bundles and launch tests.
 * <p/>
 * It contributes the following DSL to describe a testing project:
 * <pre>
 * eclipseTest {
 *     fragmentHost 'host.plugin.id'
 *     applicationName 'org.eclipse.pde.junit.runtime.coretestapplication'
 *     optionsFile file('.options')
 * }
 * </pre>
 * If the test project is an Eclipse plug-in fragment, then the the {@code fragmentHost} specifies
 * the host plug-in's ID (not mandatory). The {@code applicationName} is the PDE test runner class.
 * The {@code optionsFile} specifies a file containing extra arguments for the testing (not
 * mandatory).
 * <p/>
 * The tests are launched with PDE. The process is: (1) Copy the target platform to the build
 * folder. (2) Install the test plug-in and it's dependencies into the copied target platform with
 * P2. (3) Launch Eclipse with the PDE testing application (4) Collect the test results.
 * <p/>
 * The way how the tests are collected from the testing project and how the results are collected is
 * defined in the {@link eclipsebuild.testing} package.
 * <p/>
 * More information on the PDE testing automation:
 * <a href="http://www.eclipse.org/articles/Article-PDEJUnitAntAutomation/">
 * http://www.eclipse.org/articles/Article-PDEJUnitAntAutomation</a>.
 */
class TestBundlePlugin implements Plugin<Project> {

    // name of the root node in the DSL
    static String DSL_EXTENSION_NAME = "eclipseTest"

    // task names
    static final TASK_NAME_ECLIPSE_TEST = 'eclipseTest'

    static final TASK_NAME_CROSS_VERSION_ECLIPSE_TEST = 'crossVersionEclipseTest'

    public final FileResolver fileResolver

    @Inject
    public TestBundlePlugin(FileResolver fileResolver) {
        this.fileResolver = fileResolver
    }

    @Override
    public void apply(Project project) {
        configureProject(project)
        validateDslBeforeBuildStarts(project)
        addTaskCreateEclipseTest(project)
    }

    static void configureProject(Project project) {
        project.extensions.create(DSL_EXTENSION_NAME, EclipseTestExtension)
        project.getPlugins().apply(eclipsebuild.BundlePlugin)

        // append the sources of each first-level dependency and its transitive dependencies of
        // the 'bundled' configuration to the 'bundledSource' configuration
        project.afterEvaluate {
            project.configurations.bundled.resolvedConfiguration.firstLevelModuleDependencies.each { dep ->
                addSourcesRecursively(project, dep)
            }
        }
    }

    private static def addSourcesRecursively(project, dep) {
        project.dependencies {
            bundledSource group: dep.moduleGroup, name: dep.moduleName, version: dep.moduleVersion, classifier: 'sources'
        }
        dep.children.each { childDep -> addSourcesRecursively(project, childDep) }
    }

    static void validateDslBeforeBuildStarts(Project project) {
        project.gradle.taskGraph.whenReady {
            // the eclipse application must be defined
            assert project.eclipseTest.applicationName != null
        }
    }

    static void addTaskCreateEclipseTest(Project project) {
        Config config = Config.on(project)

        String taskDescription = 'Executes all Eclipse integration tests using PDE test runner'
        def eclipseTest = defineEclipseTestTask(project, config, TASK_NAME_ECLIPSE_TEST, taskDescription, "latest")
        project.tasks.check.dependsOn eclipseTest

        taskDescription = 'Executes all Eclipse integration tests including the cross-version coverage using PDE Test runner'
        defineEclipseTestTask(project, config, TASK_NAME_CROSS_VERSION_ECLIPSE_TEST, taskDescription, "partial")
    }

    static Task defineEclipseTestTask(Project project, Config config, String testTaskName, String taskDescription, String integTestVersions) {
        Test testTask = project.task(testTaskName, type: EclipseTestTask) {
            group = Constants.gradleTaskGroupName
            description = taskDescription

            // configure the test runner to execute all classes from the project
            testExecuter = new EclipseTestExecuter(project, services.get(BuildOperationExecutor.class))
            testClassesDirs =  project.sourceSets.main.output.classesDirs
            classpath = project.sourceSets.main.output + project.sourceSets.test.output
            reports.html.destination = new File("${project.reporting.baseDir}/eclipseTest")

            // set some system properties for the test Eclipse
            systemProperty('osgi.requiredJavaVersion','1.8')
            systemProperty('eclipse.pde.launch','true')
            systemProperty('eclipse.p2.data.area','@config.dir/p2')
            systemProperty('integtest.versions', integTestVersions)

            // set the task outputs
            def testDistributionDir = project.file("$project.buildDir/eclipseTest/eclipse")
            def additionalPluginsDir = project.file("$project.buildDir/eclipseTest/additions")
            outputs.dir testDistributionDir
            outputs.dir additionalPluginsDir

            // the input for the task 'eclipseTest' is the output jars from the dependent projects
            // consequently we have to set it after the project is evaluated
//            project.afterEvaluate {
//                for (dependency in compileClasspathProjectDependencies(project)) {
//                    def taskHandler = dependency.tasks.findByPath("jar")
//                    if (taskHandler != null) {
//                        inputs.files taskHandler.outputs.files
//                    }
//                }
//            }

            doFirst { beforeEclipseTest(project, config, testDistributionDir, additionalPluginsDir) }
        }

        testTask.dependsOn 'test'
        testTask.dependsOn 'jar'
    }

    static void beforeEclipseTest(Project project, Config config, File testDistributionDir, File additionalPluginsDir) {
        // before testing, create a fresh eclipse IDE with all dependent plugins installed
        // first delete the test eclipse distribution and the original plugins.
        project.logger.info("Delete '${testDistributionDir.absolutePath}'")
        testDistributionDir.deleteDir()
        project.logger.info("Delete '${additionalPluginsDir.absolutePath}'")
        additionalPluginsDir.deleteDir()

        // copy the target platform to the test distribution folder
        project.logger.info("Copy target platform from '${config.nonMavenizedTargetPlatformDir.absolutePath}' into the build folder '${testDistributionDir.absolutePath}'")
        copyTargetPlatformToBuildFolder(project, config, testDistributionDir)
        fixEclipseIni(testDistributionDir)

        // publish the dependencies' output jars into a P2 repository in the additions folder
        project.logger.info("Create mini-update site from the test plug-in and its dependencies at '${additionalPluginsDir.absolutePath}'")
        publishDependenciesIntoTemporaryRepo(project, config, additionalPluginsDir)

        // install all elements from the P2 repository into the test Eclipse distribution
        project.logger.info("Install the test plug-in and its dependencies from '${additionalPluginsDir.absolutePath}' into '${testDistributionDir.absolutePath}'")
        installDepedenciesIntoTargetPlatform(project, config, additionalPluginsDir, testDistributionDir)
    }


    static void copyTargetPlatformToBuildFolder(Project project, Config config,  File distro) {
        project.copy {
            from config.nonMavenizedTargetPlatformDir
            into distro
        }
    }

    static void fixEclipseIni(File eclipseDir) {
        File eclipseIni = new File(eclipseDir, "eclipse.ini")
        List<String> lines = eclipseIni.readLines()
        List<String> updateLines = []

        int lineNum = 0
        lines.each { line ->
            if (lineNum == 1 && line.startsWith("../target-platform/plugins/org.eclipse.equinox.launcher")) {
                updateLines.add(line.replace("../target-platform/", "../eclipse/"))
            } else if (lineNum == 3 && line.startsWith("../target-platform/plugins/org.eclipse.equinox.launcher")) {
                updateLines.add(line.replace("../target-platform/", "../eclipse/"))
            } else {
                updateLines.add(line)
            }
            lineNum++
        }

        eclipseIni.withWriter { writer ->
            updateLines.each {line ->
                writer.writeLine(line)
            }
        }
    }

    static void publishDependenciesIntoTemporaryRepo(Project project, Config config, File additionalPluginsDir) {
        // take all direct dependencies and and publish their jar archive to the build folder
        // (eclipsetest/additions subfolder) as a mini P2 update site
        for (Project p : compileClasspathProjectDependencies(project)) {
            project.logger.debug("Publish '${p.tasks.jar.outputs.files.singleFile.absolutePath}' to '${additionalPluginsDir.path}/${p.name}'")
            project.exec {
                commandLine(config.eclipseSdkExe,
                        "-application", "org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher",
                        "-metadataRepository", "file:${additionalPluginsDir.path}/${p.name}",
                        "-artifactRepository", "file:${additionalPluginsDir.path}/${p.name}",
                        "-bundles", p.tasks.jar.outputs.files.singleFile.path,
                        "-publishArtifacts",
                        "-nosplash",
                        '-vm', System.getProperty('java.home') + '/bin/java',
                        "-consoleLog")
            }
        }

        // and do the same with the current plugin
        project.logger.debug("Publish '${project.jar.outputs.files.singleFile.absolutePath}' to '${additionalPluginsDir.path}/${project.name}'")
        project.exec {
            commandLine(config.eclipseSdkExe,
                    "-application", "org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher",
                    "-metadataRepository", "file:${additionalPluginsDir.path}/${project.name}",
                    "-artifactRepository", "file:${additionalPluginsDir.path}/${project.name}",
                    "-bundles", project.jar.outputs.files.singleFile.path,
                    "-publishArtifacts",
                    "-nosplash",
                    '-vm', System.getProperty('java.home') + '/bin/java',
                    "-consoleLog")
        }
    }

    static void installDepedenciesIntoTargetPlatform(Project project, Config config, File additionalPluginsDir, File testDistributionDir) {
        // take the mini P2 update sites from the build folder and install it into the test Eclipse distribution
        for (Project p : compileClasspathProjectDependencies(project)) {
            project.logger.info("Install '${additionalPluginsDir.path}/${p.name}' into '${testDistributionDir.absolutePath}'")
            project.exec {
                commandLine(config.eclipseSdkExe,
                        '-application', 'org.eclipse.equinox.p2.director',
                        '-repository', "file:${additionalPluginsDir.path}/${p.name}",
                        '-installIU', p.name,
                        '-destination', testDistributionDir,
                        '-profile', 'SDKProfile',
                        '-p2.os', Constants.os,
                        '-p2.ws', Constants.ws,
                        '-p2.arch', Constants.arch,
                        '-roaming',
                        '-nosplash',
                        '-vm', System.getProperty('java.home') + '/bin/java',
                        '-consoleLog')
            }
        }

        // do the same with the current project
        project.logger.info("Install '${additionalPluginsDir.path}/${project.name}' into '${testDistributionDir.absolutePath}'")
        project.exec {
            commandLine(config.eclipseSdkExe,
                    '-application', 'org.eclipse.equinox.p2.director',
                    '-repository', "file:${additionalPluginsDir.path}/${project.name}",
                    '-installIU', project.name,
                    '-destination', testDistributionDir,
                    '-profile', 'SDKProfile',
                    '-p2.os', Constants.os,
                    '-p2.ws', Constants.ws,
                    '-p2.arch', Constants.arch,
                    '-roaming',
                    '-vm', System.getProperty('java.home') + '/bin/java',
                    '-nosplash')
        }
    }

    static def compileClasspathProjectDependencies(Project project) {
        List<Project> result = []
        def resolvedArtifacts = project.configurations.compileClasspath.resolvedConfiguration.resolvedArtifacts
        for (artifact in resolvedArtifacts) {
            def identifier = artifact.id.componentIdentifier
            if (identifier instanceof ProjectComponentIdentifier) {
                result += project.rootProject.project(identifier.projectPath)
            }
        }
        result.reverse()
    }

}
