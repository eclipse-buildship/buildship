package eclipsebuild.jar


import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
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

    @Input
    abstract Property<String> getSourceReference()

    @InputFile
    abstract RegularFileProperty getJarFile()

    @InputDirectory
    abstract DirectoryProperty getExtraResourcesDirectory()

    @Input
    abstract Property<String> getProjectName()

    @InputFiles
    abstract SetProperty<File> getAllSrcDirs()

    @InputFile
    abstract Property<File> getFirstDependencyJar()

    @InputFile
    abstract Property<File> getFirstDependencySourceJar()

    @TaskAction
    void convertOsgiBundle() {
        createNewBundle(getFirstDependencyJar().get(), getFirstDependencySourceJar().get())
    }

    void createNewBundle(File dependencyJar, File dependencySourceJar) {
        String manifest = JarBundleUtils.manifestContent([dependencyJar] + getJarFile().get().getAsFile(), template.get(), packageFilter.get(), bundleVersion.get(), qualifier.get(), sourceReference.get())
        File manifestFile = new File(getExtraResourcesDirectory().get().getAsFile(), '/META-INF/MANIFEST.MF')
        manifestFile.parentFile.mkdirs()
        manifestFile.text = manifest

        // binary jar
        File osgiJar = new File(outputDirectory.get().asFile, "osgi_${getProjectName().get()}.jar")
        ant.zip(destfile: osgiJar) {
            zipfileset(src: getJarFile().get().getAsFile(), excludes: 'META-INF/MANIFEST.MF')
            zipfileset(src: dependencyJar, excludes: 'META-INF/MANIFEST.MF')
        }

        ant.zip(update: 'true', destfile: osgiJar) {
            fileset(dir: getExtraResourcesDirectory().get().getAsFile())
        }

        resources.files.each { File resource ->
            ant.zip(update: 'true', destfile: osgiJar) {
                fileset(dir: resource)
            }
        }

        // source jar
        File osgiSourceJar = new File(outputSourceDirectory.get().asFile, "osgi_${getProjectName().get()}.source.jar")
        ant.zip(destfile: osgiSourceJar) {
            getAllSrcDirs().get().forEach { File srcDir ->
                if (srcDir.exists()) {
                    fileset(dir: srcDir.absolutePath, excludes: 'META-INF/MANIFEST.MF')
                }
            }
            zipfileset(src: dependencySourceJar, excludes: 'META-INF/MANIFEST.MF')
        }

        ant.zip(update: 'true', destfile: osgiSourceJar) {
            fileset(dir: getExtraResourcesDirectory().get().getAsFile())
        }

        resources.files.each { File resource ->
            ant.zip(update: 'true', destfile: osgiSourceJar) {
                fileset(dir: resource)
            }
        }
    }
}
