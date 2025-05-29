package eclipsebuild.jar

import eclipsebuild.BuildDefinitionPlugin
import eclipsebuild.Constants
import eclipsebuild.PluginUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Property
import org.gradle.api.tasks.bundling.Jar
import org.gradle.plugins.ide.eclipse.EclipsePlugin
import org.gradle.plugins.ide.eclipse.model.Library

class ExistingJarBundlePlugin implements Plugin<Project> {

    static final String TASK_NAME_CONVERT_TO_BUNDLE = 'convertToOsgiBundle'
    static final String TASK_NAME_CREATE_P2_REPOSITORY = 'createP2Repository'
    static final String PLUGIN_CONFIGURATION_NAME = 'plugin'
    static final String BUNDLES_STAGING_FOLDER = 'tmp/bundles'
    static final String P2_REPOSITORY_FOLDER = 'repository'

    static class BundleInfoExtension {
        final Project project
        final Property<String> bundleName
        final Property<String> bundleVersion
        final Property<String> qualifier
        final Property<String> template
        final Property<String> packageFilter
        final ConfigurableFileCollection resources

        BundleInfoExtension(Project project) {
            this.project = project
            bundleName = project.objects.property(String)
            bundleVersion = project.objects.property(String)
            qualifier = project.objects.property(String)
            template = project.objects.property(String)
            packageFilter = project.objects.property(String)
            resources = project.files()
        }

        void setResources(FileCollection resources) {
            this.resources.setFrom(resources)
        }
    }

    @Override
    void apply(Project project) {
        project.getPluginManager().apply(JavaPlugin.class)

        configureExtensions(project)
        configureConfigurations(project)

        addGenerateEclipseProjectTask(project)
        addConvertOsgiBundleTask(project)
        addCreateP2RepositoryTask(project)
    }

    private void addGenerateEclipseProjectTask(Project project) {
        project.plugins.withType(EclipsePlugin) {

            project.eclipse.project.natures 'org.eclipse.pde.PluginNature'
            project.eclipse.project.buildCommand 'org.eclipse.pde.ManifestBuilder'
            project.eclipse.project.buildCommand 'org.eclipse.pde.SchemaBuilder'

            // add jar file to the Eclipse project's classpath
            project.eclipse.classpath.file.whenMerged {
                def lib = new Library(fileReference(jarName(project)))
                lib.exported = true
                lib.sourcePath = fileReference(sourceJarName(project))
                entries += lib
            }
            project.tasks[EclipsePlugin.ECLIPSE_CP_TASK_NAME].dependsOn TASK_NAME_CONVERT_TO_BUNDLE
            project.tasks[EclipsePlugin.ECLIPSE_CP_TASK_NAME].doLast {
                // copy jar file into project
                File depJar = JarBundleUtils.firstDependencyJar(getPluginConfiguration(project))

                List<File> osgiTaskOutputFiles = ((ConvertOsgiBundleTask) project.tasks[TASK_NAME_CONVERT_TO_BUNDLE]).outputDirectory.get().asFile.listFiles().toList()
                List<File> osgiTaskOutputSrcFiles = ((ConvertOsgiBundleTask) project.tasks[TASK_NAME_CONVERT_TO_BUNDLE]).outputSourceDirectory.get().asFile.listFiles().toList()

                File osgiJar = osgiTaskOutputFiles[0]
                project.copy {
                    from osgiJar
                    into project.file('.')
                    rename { jarName(project) }
                }

                // copy the source jar file into project
                File osgiSourceJar = osgiTaskOutputSrcFiles[0]
                project.copy {
                    from(osgiSourceJar)
                    into project.file('.')
                    rename { sourceJarName(project) }
                }


                // update manifest file
                String template = project.extensions.bundleInfo.template.get()
                String packageFilter = project.extensions.bundleInfo.packageFilter.get()
                String bundleVersion = project.extensions.bundleInfo.bundleVersion.get()
                String qualifier = 'qualifier'
                project.file('META-INF').mkdirs()
                project.file('META-INF/MANIFEST.MF').text = JarBundleUtils.manifestContent([depJar, osgiJar], template, packageFilter, bundleVersion, qualifier).replace("Bundle-ClassPath: .", "Bundle-ClassPath: ${jarName(project)}")
            }
        }
    }

    private void addConvertOsgiBundleTask(Project project) {
        project.tasks.create(TASK_NAME_CONVERT_TO_BUNDLE, ConvertOsgiBundleTask) {
            group = Constants.gradleTaskGroupName
            dependsOn project.getConfigurations().getByName(PLUGIN_CONFIGURATION_NAME)
            dependsOn 'jar'
            bundleName.convention(project.extensions.bundleInfo.bundleName)
            bundleVersion.convention(project.extensions.bundleInfo.bundleVersion)
            qualifier.convention(project.extensions.bundleInfo.qualifier)
            template.convention(project.extensions.bundleInfo.template)
            packageFilter.convention(project.extensions.bundleInfo.packageFilter)
            resources.from(project.extensions.bundleInfo.resources)
            outputDirectory.convention(project.layout.buildDirectory.dir("$BUNDLES_STAGING_FOLDER/plugins"))
            outputSourceDirectory.convention(project.layout.buildDirectory.dir("$BUNDLES_STAGING_FOLDER/plugin-sources"))
            pluginConfiguration.convention(getPluginConfiguration(project))
            sourceReference.convention(project.provider { PluginUtils.sourceReference(project) })
            jarFile.convention(project.tasks.named('jar', Jar).flatMap { it.archiveFile })
            extraResourcesDirectory.convention(project.layout.buildDirectory.dir("tmp/bundle-resources"))
            getProjectName().convention(project.name)
            getAllSrcDirs().convention(project.provider { project.sourceSets.main.allSource.srcDirs })
            getFirstDependencyJar().convention(project.provider { JarBundleUtils.firstDependencyJar(pluginConfiguration.get()) })
            getFirstDependencySourceJar().convention(project.provider { JarBundleUtils.firstDependencySourceJar(project, pluginConfiguration.get()) })
        }
    }

    private void addCreateP2RepositoryTask(Project project) {
         def task = project.tasks.create(TASK_NAME_CREATE_P2_REPOSITORY, CreateP2RepositoryTask) {
            group = Constants.gradleTaskGroupName
            dependsOn ":${BuildDefinitionPlugin.TASK_NAME_VALIDATE_ECLIPSE_SDK}"
            dependsOn TASK_NAME_CONVERT_TO_BUNDLE

            bundleSourceDir = new File(project.buildDir, BUNDLES_STAGING_FOLDER)
            targetRepositoryDir = new File(project.buildDir, P2_REPOSITORY_FOLDER)
        }
    }

    private void configureExtensions(Project project) {
        project.extensions.create('bundleInfo', BundleInfoExtension, project)
    }

    private void configureConfigurations(Project project) {
        ConfigurationContainer configurations = project.getConfigurations()
        configurations.create(PLUGIN_CONFIGURATION_NAME)
            .setVisible(false)
            .setTransitive(false)
            .setDescription("Classpath for deployable plugin jars, not transitive")
    }

    private Configuration getPluginConfiguration(Project project) {
        project.configurations.getByName(ExistingJarBundlePlugin.PLUGIN_CONFIGURATION_NAME)
    }

    private static String jarName(Project project) {
        String bundleName = project.extensions.bundleInfo.bundleName.get()
        String bundleVersion = project.extensions.bundleInfo.bundleVersion.get()
        "${bundleName}_${bundleVersion}.jar"
    }

    private static String sourceJarName(Project project) {
        String bundleName = project.extensions.bundleInfo.bundleName.get()
        String bundleVersion = project.extensions.bundleInfo.bundleVersion.get()
        "${bundleName}.source_${bundleVersion}.jar"
    }
}
