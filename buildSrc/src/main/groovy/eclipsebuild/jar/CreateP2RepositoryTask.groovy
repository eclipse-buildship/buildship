package eclipsebuild.jar

import eclipsebuild.Config
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

class CreateP2RepositoryTask extends DefaultTask {

    @InputDirectory
    File bundleSourceDir

    @OutputDirectory
    File targetRepositoryDir

    @TaskAction
    def createP2Repository() {
        project.logger.info("Publish plugins and features from '${bundleSourceDir.absolutePath}' to the update site '${targetRepositoryDir.absolutePath}'")
        project.exec {
            commandLine(Config.on(project).eclipseSdkExe,
                '-nosplash',
                '-application', 'org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher',
                '-metadataRepository', targetRepositoryDir.toURI().toURL(),
                '-artifactRepository', targetRepositoryDir.toURI().toURL(),
                '-source', bundleSourceDir,
                '-publishArtifacts',
                '-vm', System.getProperty('java.home') + '/bin/java',
                '-configs', 'ANY')
        }
    }
}
