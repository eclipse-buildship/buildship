package eclipsebuild

import groovy.xml.XmlSlurper

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.LogLevel
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations

import javax.inject.Inject

/**
 * Assembles an Eclipse distribution from a target definition file by running the p2 director.
 * <p/>
 * The result is a pure function of the target definition, the platform it is assembled for and the Eclipse SDK that
 * runs the director, so it can be taken from the build cache instead of being downloaded and installed again.
 * <p/>
 * No other task may write to the output directory. The locally built jar bundles are installed into a copy of it by
 * {@code addExistingJarBundlesToTargetPlatform}, which leaves this task's result untouched while those bundles are
 * rebuilt.
 */
@CacheableTask
abstract class AssembleTargetPlatformTask extends DefaultTask {

    /**
     * Placeholder that a target definition can use to reference a p2 repository stored inside the project.
     */
    static final String PROJECT_LOCATION_TOKEN = '\${project_loc}'

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    abstract RegularFileProperty getTargetPlatformFile()

    @OutputDirectory
    abstract DirectoryProperty getTargetPlatformBaseDir()

    @Inject
    abstract ExecOperations getExecOperations()

    /**
     * The version of the Eclipse SDK whose p2 director assembles the distribution. A director from a different SDK can
     * generate different p2 metadata, so the version belongs in the cache key.
     */
    @Input
    abstract Property<String> getEclipseSdkVersion()

    /**
     * The download classifier of the Eclipse SDK, for example {@code linux-gtk-x86_64}.
     */
    @Input
    abstract Property<String> getEclipseSdkClassifier()

    /**
     * The operating system, windowing system and architecture the distribution is assembled for. The p2 director
     * installs different bundles for each combination, so a distribution assembled for one must never be reused for
     * another.
     */
    @Input
    abstract Property<String> getOs()

    @Input
    abstract Property<String> getWs()

    @Input
    abstract Property<String> getArch()

    /**
     * The Eclipse SDK executable. Its path lies inside the Gradle user home and therefore differs on every machine,
     * so it is deliberately kept out of the cache key; {@link #getEclipseSdkVersion()} identifies the SDK instead.
     */
    @Internal
    abstract Property<String> getEclipseSdkExe()

    /**
     * Mirrors of the update sites named in the target definition, as {@code sourceUrl->targetUrl} pairs.
     * <p/>
     * A mirror is a different route to the same content, so it must not change the cache key. Were it an input, a
     * distribution assembled on CI, which passes mirrors, could never be reused by a local build, which does not.
     */
    @Internal
    abstract Property<String> getRepositoryMirrorUrls()

    /**
     * The directory that {@link #PROJECT_LOCATION_TOKEN} expands to. It is an absolute path, so it cannot be part of
     * the cache key, and a target definition that uses the token reads a repository this task does not track. Such a
     * definition therefore disables caching entirely.
     */
    @Internal
    abstract Property<String> getProjectLocation()

    /**
     * Whether a target definition pulls content from a p2 repository stored inside the project.
     * <p/>
     * Such a repository is not a declared input of this task, so its content is invisible to the cache key. A
     * definition that reads one therefore must not be cached.
     *
     * @param targetDefinition the target definition file
     * @return whether the definition references a project-local repository
     */
    static boolean readsProjectLocalRepository(File targetDefinition) {
        targetDefinition.text.contains(PROJECT_LOCATION_TOKEN)
    }

    AssembleTargetPlatformTask() {
        outputs.cacheIf('the target definition reads no project-local p2 repository') {
            !readsProjectLocalRepository(getTargetPlatformFile().get().asFile)
        }
    }

    @TaskAction
    void assembleTargetPlatform() {
        File targetPlatform = getTargetPlatformBaseDir().get().asFile

        // delete the target platform directory to ensure that the P2 Director creates a fresh product
        if (targetPlatform.exists()) {
            getLogger().info("Delete target platform directory '${targetPlatform}'")
            targetPlatform.deleteDir()
        }

        Map<String, String> mirrors = parseMirrors()

        // collect  update sites and feature names
        def updateSites = []
        def features = []
        def rootNode = new XmlSlurper().parseText(getTargetPlatformFile().get().getAsFile().text)
        rootNode.locations.location.each { location ->
            String siteUrl = location.repository.@location.text().replace(PROJECT_LOCATION_TOKEN, 'file://' + getProjectLocation().get())
            if (mirrors[siteUrl]) {
                updateSites.add(mirrors[siteUrl])
            }
            updateSites.add(siteUrl)
            location.unit.each { unit -> features.add("${unit.@id}/${unit.@version}") }
        }

        // invoke the P2 director application to assemble install all features from the target
        // definition file to the target platform: http://help.eclipse.org/luna/index.jsp?topic=%2Forg.eclipse.platform.doc.isv%2Fguide%2Fp2_director.html
        getLogger().info("Assemble target platfrom in '${targetPlatform.absolutePath}'.\n    Update sites: '${updateSites.join(' ')}'\n    Features: '${features.join(' ')}'")

        executeP2Director(updateSites.join(','), features.join(','))

        targetPlatform.mkdirs()
    }

    private Map<String, String> parseMirrors() {
        Map<String, String> mirrors = [:]
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
        mirrors
    }

    private void executeP2Director(String repositoryUrl, String installIU) {
        String destination = getTargetPlatformBaseDir().get().asFile.path

        getExecOperations().exec {

            // redirect the external process output to the logging
            it.standardOutput = new LogOutputStream(getLogger(), LogLevel.INFO, LogOutputStream.Type.STDOUT)
            it.errorOutput = new LogOutputStream(getLogger(), LogLevel.INFO, LogOutputStream.Type.STDERR)

            it.commandLine(getEclipseSdkExe().get(),
                '-application', 'org.eclipse.equinox.p2.director',
                '-repository', repositoryUrl,
                '-uninstallIU', installIU,
                '-tag', 'target-platform',
                '-destination', destination,
                '-profile', 'SDKProfile',
                '-bundlepool', destination,
                '-p2.os', getOs().get(),
                '-p2.ws', getWs().get(),
                '-p2.arch', getArch().get(),
                '-roaming',
                '-nosplash',
                '-consoleLog',
                *Constants.p2VmArgs)

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
                '-destination', destination,
                '-profile', 'SDKProfile',
                '-bundlepool', destination,
                '-p2.os', getOs().get(),
                '-p2.ws', getWs().get(),
                '-p2.arch', getArch().get(),
                '-roaming',
                '-nosplash',
                '-consoleLog',
                *Constants.p2VmArgs)
        }
    }
}
