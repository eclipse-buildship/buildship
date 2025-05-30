package eclipsebuild


import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.LogLevel
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations

import javax.inject.Inject

abstract class AssembleTargetPlatformTask extends DefaultTask {

    @InputFile
    abstract RegularFileProperty getTargetPlatformFile()

    @OutputDirectory
    abstract DirectoryProperty getNonMavenizedTargetPlatformDir()

    @Inject
    abstract ExecOperations getExecOperations()

    @Input
    abstract Property<String> getEclipseSdkExe()

    @Optional
    @Input
    abstract Property<String> getRepositoryMirrorUrls()

    @TaskAction
    void assembleTargetPlatform() {
        // if multiple builds start on the same machine (which is the case with a CI server)
        // we want to prevent them assembling the same target platform at the same time
        def lock = new FileSemaphore(nonMavenizedTargetPlatformDir.get().getAsFile())
        try {
            lock.lock()
            assembleTargetPlatformUnprotected(getProject())
        } finally {
            lock.unlock()
        }
    }

    void assembleTargetPlatformUnprotected(Project project) {
        // delete the target platform directory to ensure that the P2 Director creates a fresh product
        if (nonMavenizedTargetPlatformDir.get().getAsFile().exists()) {
            getLogger().info("Delete mavenized platform directory '${nonMavenizedTargetPlatformDir.get().getAsFile()}'")
            nonMavenizedTargetPlatformDir.get().getAsFile().deleteDir()
        }

        // repository mirrors
        def mirrors = [:]
        if (getRepositoryMirrorUrls().isPresent()) {
            String allMirrors = getRepositoryMirrorUrls().get()
            allMirrors.split(',').each {
                if (!it.contains("->")) {
                    throw new RuntimeException("Mirrors should be denoted as sourceUrl->targetUrl")
                }
                def mirror = it.split('->')
                mirrors[mirror[0]] = mirror[1]
            }
        }

        // collect  update sites and feature names
        def updateSites = []
        def features = []
        def rootNode = new XmlSlurper().parseText(getTargetPlatformFile().get().getAsFile().text)
        rootNode.locations.location.each { location ->
            String siteUrl = location.repository.@location.text().replace('\${project_loc}', 'file://' + project.projectDir.absolutePath)
            if (mirrors[siteUrl]) {
                updateSites.add(mirrors[siteUrl])
            }
            updateSites.add(siteUrl)
            location.unit.each { unit -> features.add("${unit.@id}/${unit.@version}") }
        }

        // invoke the P2 director application to assemble install all features from the target
        // definition file to the target platform: http://help.eclipse.org/luna/index.jsp?topic=%2Forg.eclipse.platform.doc.isv%2Fguide%2Fp2_director.html
        getLogger().info("Assemble target platfrom in '${nonMavenizedTargetPlatformDir.get().getAsFile().absolutePath}'.\n    Update sites: '${updateSites.join(' ')}'\n    Features: '${features.join(' ')}'")

        executeP2Director(updateSites.join(','), features.join(','))

        nonMavenizedTargetPlatformDir.get().getAsFile().mkdirs()
        new File(nonMavenizedTargetPlatformDir.get().getAsFile(), 'digest').text = BuildDefinitionPlugin.targetPlatformHash(project, getTargetPlatformFile().get().getAsFile().text)
    }

    private void executeP2Director(String repositoryUrl, String installIU) {
        getExecOperations().exec {

            // redirect the external process output to the logging
            it.standardOutput = new LogOutputStream(getLogger(), LogLevel.INFO, LogOutputStream.Type.STDOUT)
            it.errorOutput = new LogOutputStream(getLogger(), LogLevel.INFO, LogOutputStream.Type.STDERR)

            it.commandLine(getEclipseSdkExe().get(),
                '-application', 'org.eclipse.equinox.p2.director',
                '-repository', repositoryUrl,
                '-uninstallIU', installIU,
                '-tag', 'target-platform',
                '-destination', nonMavenizedTargetPlatformDir.get().getAsFile().path,
                '-profile', 'SDKProfile',
                '-bundlepool', nonMavenizedTargetPlatformDir.get().getAsFile().path,
                '-p2.os', Constants.os,
                '-p2.ws', Constants.ws,
                '-p2.arch', Constants.arch,
                '-roaming',
                '-nosplash',
                '-consoleLog',
                '-vmargs', '-Declipse.p2.mirror=false')

            it.ignoreExitValue = true
        }

        getExecOperations().exec {

            // redirect the external process output to the logging
            it.standardOutput = new LogOutputStream(getLogger(), LogLevel.INFO, LogOutputStream.Type.STDOUT)
            it.errorOutput = new LogOutputStream(getLogger(), LogLevel.INFO, LogOutputStream.Type.STDERR)

            it.commandLine(getEclipseSdkExe().get(),
                '-application', 'org.eclipse.equinox.p2.director',
                '-repository', repositoryUrl,
                '-installIU', installIU,
                '-tag', 'target-platform',
                '-destination', nonMavenizedTargetPlatformDir.get().getAsFile().path,
                '-profile', 'SDKProfile',
                '-bundlepool', nonMavenizedTargetPlatformDir.get().getAsFile().path,
                '-p2.os', Constants.os,
                '-p2.ws', Constants.ws,
                '-p2.arch', Constants.arch,
                '-roaming',
                '-nosplash',
                '-consoleLog',
                '-vmargs', '-Declipse.p2.mirror=false')
        }
    }
}
