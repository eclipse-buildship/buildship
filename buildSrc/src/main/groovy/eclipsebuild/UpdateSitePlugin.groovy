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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPlugin

/**
 * Gradle plugin for building Eclipse update sites.
 * <p>
 * An example for a valid DSL:
 * <pre>
 * apply plugin: eclipsebuild.UpdateSitePlugin
 *
 * updateSite {
 *   siteDescriptor = file('category.xml')
 *   extraResources = files('epl-v10.html', 'readme.txt')
 *   p2ExtraProperties = ['p2.mirrorsURL' : 'http://www.eclipse.org/downloads/download.php?file=/path/to/repository&format=xml' ]
 *   signing = { ... }
 * }
 * </pre>
 * The {@code siteDescriptor} is the category definition for the P2 update site.
 * The {@code extraResources} enumerates all extra files that should be included in the update site.
 * The {@code signing} closure defining how the update site's artifacts should be signed.
 * The {@code mutateArtifactsXml} enables the project to transform the artifacts.xml file after
 * it is generated to provide extra information about the update site.
 * <p>
 * The main tasks contributed by this plugin are responsible to generate an Eclipse Update Site.
 * They are attached to the 'assemble' task. When executed, all project dependency jars are copied
 * to the build folder, signed and published to the buildDir/repository folder. To include a
 * the local plugin or feature project, the {@code localPlugin} and {@code localFeature}
 * configuration scope should be used. An external plugin can be also included by using the
 * {@code externalPlugin} configuration scope. The last configuration scope is the
 * {@code signedExternalPlugin} which is the same as the externalPlugin except the content
 * is not signed nor conditioned with pack200 when publishing.
 */
class UpdateSitePlugin implements Plugin<Project> {

    /**
     * Extension class to configure the UpdateSite plugin.
     */
    static class Extension {
        File siteDescriptor
        FileCollection extraResources
        Closure signing
        Closure mutateArtifactsXml
    }

    // name of the root node in the DSL
    static final String DSL_EXTENSION_NAME = 'updateSite'

    // task names defined in the plug-in
    static final String COPY_BUNDLES_TASK_NAME = 'copyBundles'
    static final String SIGN_BUNDLES_TASK_NAME = 'signBundles'
    static final String COMPRESS_BUNDLES_TASK_NAME = 'compressBundles'
    static final String CREATE_P2_REPOSITORY_TASK_NAME = 'createP2Repository'

    // temporary folder names during build
    static final String UNSIGNED_BUNDLES_DIR_NAME = 'unsigned-bundles'
    static final String SIGNED_BUNDLES_DIR_NAME = 'signed-bundles'
    static final String COMPRESSED_BUNDLES_DIR_NAME = 'compressed-bundles'
    static final String FEATURES_DIR_NAME = 'features'
    static final String PLUGINS_DIR_NAME = 'plugins'
    static final String REPOSITORY_DIR_NAME = 'repository'

    @Override
    void apply(Project project) {
        configureProject(project)
        addTaskCopyBundles(project)
        addTaskSignBundles(project)
        addTaskCompressBundles(project)
        addTaskCreateP2Repository(project)
    }

    static void configureProject(Project project) {
        // apply the Java plugin to have the life-cycle tasks
        project.plugins.apply(JavaPlugin)

        // create scopes for local and external plugins and features
        project.configurations.create('localPlugin')
        project.configurations.create('localFeature')
        project.configurations.create('externalPlugin')
        project.configurations.create('signedExternalPlugin')

        // add the 'updateSite' extension
        project.extensions.create(DSL_EXTENSION_NAME, Extension)
        project.updateSite.siteDescriptor = project.file('category.xml')
        project.updateSite.extraResources = project.files()
        project.updateSite.signing = null
        project.updateSite.mutateArtifactsXml = null

        // validate the content
        validateRequiredFilesExist(project)
    }

    static void addTaskCopyBundles(Project project) {
        def copyBundlesTask = project.task(COPY_BUNDLES_TASK_NAME) {
            group = Constants.gradleTaskGroupName
            description = 'Collects the bundles that make up the update site.'
            outputs.dir new File(project.buildDir, UNSIGNED_BUNDLES_DIR_NAME)
            doLast { copyBundles(project) }
        }

        // add inputs for each plugin/feature project once this build script has been evaluated (before that, the dependencies are empty)
        project.afterEvaluate {
            for (ProjectDependency projectDependency : project.configurations.localPlugin.dependencies.withType(ProjectDependency)) {
              // check if the dependent project is a bundle or feature, once its build script has been evaluated
              def dependency = projectDependency.dependencyProject
                if (dependency.plugins.hasPlugin(BundlePlugin)) {
                    copyBundlesTask.inputs.files dependency.tasks.jar.outputs.files
                    copyBundlesTask.inputs.files dependency.tasks.sourcesJar.outputs.files
                } else {
                    dependency.afterEvaluate {
                      if (dependency.plugins.hasPlugin(BundlePlugin)) {
                          copyBundlesTask.inputs.files dependency.tasks.jar.outputs.files
                          copyBundlesTask.inputs.files dependency.tasks.sourcesJar.outputs.files
                      }
                    }
                }
            }
        }

        project.afterEvaluate {
            for (ProjectDependency projectDependency : project.configurations.localFeature.dependencies.withType(ProjectDependency)) {
              // check if the dependent project is a bundle or feature, once its build script has been evaluated
              def dependency = projectDependency.dependencyProject
                if (dependency.plugins.hasPlugin(FeaturePlugin)) {
                    copyBundlesTask.inputs.files dependency.tasks.jar.outputs.files
                } else {
                    dependency.afterEvaluate {
                        if (dependency.plugins.hasPlugin(FeaturePlugin)) {
                            copyBundlesTask.inputs.files dependency.tasks.jar.outputs.files
                            copyBundlesTask.inputs.files dependency.tasks.sourcesJar.outputs.files
                        }
                    }
                }
            }
        }
    }

    static void copyBundles(Project project) {
        def rootDir = new File(project.buildDir, UNSIGNED_BUNDLES_DIR_NAME)
        def pluginsDir = new File(rootDir, PLUGINS_DIR_NAME)
        def featuresDir = new File(rootDir, FEATURES_DIR_NAME)

        // delete old content
        if (rootDir.exists()) {
            project.logger.info("Delete bundles directory '${rootDir.absolutePath}'")
            rootDir.deleteDir()
        }

        // iterate over all the project dependencies to populate the update site with the plugins and features
        project.logger.info("Copy features and plugins to bundles directory '${rootDir.absolutePath}'")
        for (ProjectDependency projectDependency : project.configurations.localPlugin.dependencies.withType(ProjectDependency)) {
            def dependency = projectDependency.dependencyProject

            // copy the output jar for each plugin project dependency
            if (dependency.plugins.hasPlugin(BundlePlugin)) {
                project.logger.debug("Copy plugin project '${dependency.name}' with jar '${dependency.tasks.jar.outputs.files.singleFile.absolutePath}' to '${pluginsDir}'")
                project.copy {
                    from dependency.tasks.jar.outputs.files.singleFile
                    into pluginsDir
                }
                project.copy {
                    from dependency.tasks.sourcesJar.outputs.files.singleFile
                    into pluginsDir
                }
            }
        }

        for (ProjectDependency projectDependency : project.configurations.localFeature.dependencies.withType(ProjectDependency)) {
            def dependency = projectDependency.dependencyProject

            // copy the output jar for each feature project dependency
            if (dependency.plugins.hasPlugin(FeaturePlugin)) {
                project.logger.debug("Copy feature project '${dependency.name}' with jar '${dependency.tasks.jar.outputs.files.singleFile.absolutePath}' to '${pluginsDir}'")
                project.copy {
                    from dependency.tasks.jar.outputs.files.singleFile
                    into featuresDir
                }
            }
        }

        // iterate over all external dependencies and add them to the plugins (this includes the transitive dependencies)
        project.copy {
            from project.configurations.externalPlugin
            into pluginsDir
        }
        // if the signing is not enabled, then remove existing signatures
        if (project.updateSite.signing == null) {
            // extract the jars and delete the the sums from the manifest file
            pluginsDir.eachFile { jar ->
                def extractedJar = new File(jar.parentFile, jar.name + ".u")
                project.ant.unzip(src: jar, dest: new File(jar.parentFile, jar.name + ".u"))
                jar.delete()
                def manifest = new File(extractedJar, "META-INF/MANIFEST.MF")
                removeSignaturesFromManifest(manifest)
            }
            // re-jar the content without the signature files
            pluginsDir.eachFile { extractedJar ->
                def jar = new File(extractedJar.parentFile, extractedJar.name.substring(0, extractedJar.name.length() - 2))
                project.ant.zip(destfile: jar, basedir: extractedJar) {
                    exclude(name: '**/*.RSA')
                    exclude(name: '**/*.DSA')
                    exclude(name: '**/*.SF')
                }
                extractedJar.deleteDir()
            }
        }
    }

    public static def removeSignaturesFromManifest(File input) {
        def output = new StringBuilder()
        def newLineFound = false
        input.eachLine { line ->
            if (!newLineFound) {
                output.append(line)
                if (line.equals('')) {
                    newLineFound = true
                    return
                }
                output.append('\n')
            }
        }
        input.text = output.toString()
    }

    static void addTaskSignBundles(Project project) {
        project.task(SIGN_BUNDLES_TASK_NAME, dependsOn: COPY_BUNDLES_TASK_NAME) {
            group = Constants.gradleTaskGroupName
            description = 'Signs the bundles that make up the update site.'
            inputs.dir new File(project.buildDir, UNSIGNED_BUNDLES_DIR_NAME)
            outputs.dir new File(project.buildDir, SIGNED_BUNDLES_DIR_NAME)
            doLast { project.updateSite.signing(new File(project.buildDir, UNSIGNED_BUNDLES_DIR_NAME), new File(project.buildDir, SIGNED_BUNDLES_DIR_NAME)) }
            doLast { copyOverAlreadySignedBundles(project, "$SIGNED_BUNDLES_DIR_NAME/$PLUGINS_DIR_NAME") }
            onlyIf { project.updateSite.signing != null }
        }
    }

    static void copyOverAlreadySignedBundles(Project project, String folderInBuildDir) {
        project.copy {
            from project.configurations.signedExternalPlugin
            into new File(project.buildDir, folderInBuildDir)
        }
    }

    static void addTaskCompressBundles(Project project) {
        project.task(COMPRESS_BUNDLES_TASK_NAME, dependsOn: [
            COPY_BUNDLES_TASK_NAME, SIGN_BUNDLES_TASK_NAME, ":${BuildDefinitionPlugin.TASK_NAME_INSTALL_TARGET_PLATFORM}"]) {
                group = Constants.gradleTaskGroupName
                description = 'Compresses the bundles that make up the update using the pack200 tool.'
                project.afterEvaluate { inputs.dir project.updateSite.signing != null ? new File(project.buildDir, SIGNED_BUNDLES_DIR_NAME) : new File(project.buildDir, UNSIGNED_BUNDLES_DIR_NAME) }
                outputs.dir  new File(project.buildDir, COMPRESSED_BUNDLES_DIR_NAME)
                doLast { compressBundles(project) }
        }
    }

    static void compressBundles(Project project) {
        File uncompressedBundles = project.updateSite.signing != null ? new File(project.buildDir, SIGNED_BUNDLES_DIR_NAME) : new File(project.buildDir, UNSIGNED_BUNDLES_DIR_NAME)
        File compressedBundles = new File(project.buildDir, COMPRESSED_BUNDLES_DIR_NAME)

        // copy over all bundles
        project.copy {
            from uncompressedBundles
            into compressedBundles
        }

        // compress and store them in the same folder
        project.javaexec {
            main = 'org.eclipse.equinox.internal.p2.jarprocessor.Main'
            classpath Config.on(project).jarProcessorJar
            args = ['-pack',
                    '-outputDir', compressedBundles,
                    compressedBundles
            ]
        }
    }

    static void addTaskCreateP2Repository(Project project) {
        def createP2RepositoryTask = project.task(CREATE_P2_REPOSITORY_TASK_NAME, dependsOn: [
            COMPRESS_BUNDLES_TASK_NAME, ":${BuildDefinitionPlugin.TASK_NAME_INSTALL_TARGET_PLATFORM}"]) {
                group = Constants.gradleTaskGroupName
                description = 'Generates the P2 repository.'
                inputs.file project.updateSite.siteDescriptor
                inputs.files project.updateSite.extraResources
                inputs.dir new File(project.buildDir, COMPRESSED_BUNDLES_DIR_NAME)
                outputs.dir new File(project.buildDir, REPOSITORY_DIR_NAME)
                doLast { createP2Repository(project) }
        }

        project.tasks.assemble.dependsOn createP2RepositoryTask
    }

    static void createP2Repository(Project project) {
        def repositoryDir = new File(project.buildDir, REPOSITORY_DIR_NAME)

        // delete old content
        if (repositoryDir.exists()) {
            project.logger.info("Delete P2 repository directory '${repositoryDir.absolutePath}'")
            repositoryDir.deleteDir()
        }

        // create the P2 update site
        publishContentToLocalP2Repository(project, repositoryDir)

        // add custom properties to the artifacts.xml file
        def mutateArtifactsXml = project.updateSite.mutateArtifactsXml
        if (mutateArtifactsXml) {
            updateArtifactsXmlFromArchive(project, repositoryDir, mutateArtifactsXml)
        }
    }

    static void publishContentToLocalP2Repository(Project project, File repositoryDir) {
        def rootDir = new File(project.buildDir, COMPRESSED_BUNDLES_DIR_NAME)

        // publish features/plugins to the update site
        project.logger.info("Publish plugins and features from '${rootDir.absolutePath}' to the update site '${repositoryDir.absolutePath}'")
        project.exec {
            commandLine(Config.on(project).eclipseSdkExe,
                    '-nosplash',
                    '-application', 'org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher',
                    '-metadataRepository', repositoryDir.toURI().toURL(),
                    '-artifactRepository', repositoryDir.toURI().toURL(),
                    '-source', rootDir,
                    '-compress',
                    '-publishArtifacts',
                    '-reusePack200Files',
                    '-configs', 'ANY',
                    '-vm', System.getProperty('java.home') + '/bin/java',
                    '-consoleLog')
        }

        // publish P2 category defined in the category.xml to the update site
        project.logger.info("Publish categories defined in '${project.updateSite.siteDescriptor.absolutePath}' to the update site '${repositoryDir.absolutePath}'")
        project.exec {
            commandLine(Config.on(project).eclipseSdkExe,
                    '-nosplash',
                    '-application', 'org.eclipse.equinox.p2.publisher.CategoryPublisher',
                    '-metadataRepository', repositoryDir.toURI().toURL(),
                    '-categoryDefinition',  project.updateSite.siteDescriptor.toURI().toURL(),
                    '-compress',
                    '-vm', System.getProperty('java.home') + '/bin/java',
                    '-consoleLog')
        }

        // copy the extra resources to the update site
        project.copy {
            from project.updateSite.extraResources
            into repositoryDir
        }
    }

    static void updateArtifactsXmlFromArchive(Project project, File repositoryLocation, Closure mutateArtifactsXml) {
        // get the artifacts.xml file from the artifacts.jar
        def artifactsJarFile = new File(repositoryLocation, "artifacts.jar")
        def artifactsXmlFile = project.zipTree(artifactsJarFile).matching { 'artifacts.xml' }.singleFile

        // parse the xml
        def xml = new XmlParser().parse(artifactsXmlFile)

        // apply artifacts.xml customization (append mirrors url, link to stat servers, etc.)
        mutateArtifactsXml(xml)

        // write the updated artifacts.xml back to its source
        // the artifacts.xml is a temporary file hence it has to be copied back to the archive
        new XmlNodePrinter(new PrintWriter(new FileWriter(artifactsXmlFile)), "  ", "'").print(xml)
        project.ant.zip(update: true, filesonly: true, destfile: artifactsJarFile) { fileset(file: artifactsXmlFile) }
    }

    static void validateRequiredFilesExist(Project project) {
        project.gradle.taskGraph.whenReady {
            // make sure the required descriptors exist
            assert project.file(project.updateSite.siteDescriptor).exists()
        }
    }

}
