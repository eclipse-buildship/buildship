package eclipsebuild.jar

import eclipsebuild.PluginUtils
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

abstract class ConvertOsgiBundleTask extends DefaultTask {

    @Input
    abstract Property<String> getBundleName()

    @Input
    abstract Property<String> getBundleVersion()

    @Input
    abstract Property<String> getPackageFilter()

    @Input
    abstract Property<String> getQualifier()

    @Input
    abstract Property<String> getTemplate()

    @Classpath
    abstract Property<Configuration> getPluginConfiguration()

    @InputFiles
    abstract ConfigurableFileCollection getResources()

    @OutputDirectory
    abstract DirectoryProperty getOutputDirectory()

    @OutputDirectory
    abstract DirectoryProperty getOutputSourceDirectory()

    @TaskAction
    void convertOsgiBundle() {
        createNewBundle(project, JarBundleUtils.firstDependencyJar(pluginConfiguration.get()), JarBundleUtils.firstDependencySourceJar(project, pluginConfiguration.get()))
    }

    void createNewBundle(Project project, File dependencyJar, File dependencySourceJar) {
        String sourceReference = PluginUtils.sourceReference(project)
        File jarTaskOutput = project.tasks['jar'].outputs.files.asFileTree.files.first()
        String manifest = JarBundleUtils.manifestContent([dependencyJar] + jarTaskOutput, template.get(), packageFilter.get(), bundleVersion.get(), qualifier.get(), sourceReference)

        File extraResources = project.file("${project.buildDir}/tmp/bundle-resources")
        File manifestFile = new File(extraResources, '/META-INF/MANIFEST.MF')
        manifestFile.parentFile.mkdirs()
        manifestFile.text = manifest

        // binary jar
        File osgiJar = new File(outputDirectory.get().asFile, "osgi_${project.name}.jar")
        ant.zip(destfile: osgiJar) {
            zipfileset(src: jarTaskOutput, excludes: 'META-INF/MANIFEST.MF')
            zipfileset(src: dependencyJar, excludes: 'META-INF/MANIFEST.MF')
        }

        ant.zip(update: 'true', destfile: osgiJar) {
            fileset(dir: extraResources)
        }

        resources.files.each { File resource ->
            ant.zip(update: 'true', destfile: osgiJar) {
                fileset(dir: resource)
            }
        }

        // source jar
        File osgiSourceJar = new File(outputSourceDirectory.get().asFile, "osgi_${project.name}.source.jar")
        ant.zip(destfile: osgiSourceJar) {
            project.sourceSets.main.allSource.srcDirs.forEach { File srcDir ->
                if (srcDir.exists()) {
                    fileset(dir: srcDir.absolutePath, excludes: 'META-INF/MANIFEST.MF')
                }
            }
            zipfileset(src: dependencySourceJar, excludes: 'META-INF/MANIFEST.MF')
        }

        project.ant.zip(update: 'true', destfile: osgiSourceJar) {
            fileset(dir: extraResources)
        }

        resources.files.each { File resource ->
            ant.zip(update: 'true', destfile: osgiSourceJar) {
                fileset(dir: resource)
            }
        }
    }
}
