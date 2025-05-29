package eclipsebuild.jar

import eclipsebuild.Config
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

import javax.inject.Inject

abstract class CreateP2RepositoryTask extends DefaultTask {

    @InputDirectory
    File bundleSourceDir

    @Input
    abstract Property<String> getEclipseSdkExe()

    @OutputDirectory
    File targetRepositoryDir

    @Inject
    abstract ExecOperations getExecOperations()

    @TaskAction
    def createP2Repository() {
        getLogger().info("Publish plugins and features from '${bundleSourceDir.absolutePath}' to the update site '${targetRepositoryDir.absolutePath}'")
        getExecOperations().exec {
            it.commandLine(getEclipseSdkExe().get(),
                '-nosplash',
                '-application', 'org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher',
                '-metadataRepository', targetRepositoryDir.toURI().toURL(),
                '-artifactRepository', targetRepositoryDir.toURI().toURL(),
                '-source', bundleSourceDir,
                '-publishArtifacts',
                '-configs', 'ANY')
        }
    }
}
