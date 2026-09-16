package eclipsebuild.jar

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency

import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

class JarBundleUtils {

    private static final String GRADLE_BUILD_RECEIPT = 'org/gradle/build-receipt.properties'

    /**
     * Reads the build receipt embedded in a jar published from the gradle/gradle repository.
     * <p/>
     * The receipt records the version, the commit and the instant that version was built, for example
     * <pre>
     * baseVersion=8.9
     * buildTimestamp=20240711143741+0000
     * commitId=d536ef36a19186ccc596d8817123e5445f30fef8
     * </pre>
     * Bundle metadata derived from the receipt describes the wrapped Gradle release instead of the build that
     * repackaged it, so the generated bundle stays byte-identical no matter when or where it was built.
     *
     * @param jar a jar published from the gradle/gradle repository
     * @return the parsed build receipt
     */
    static Properties gradleBuildReceipt(File jar) {
        Properties receipt = new Properties()
        new ZipFile(jar).withCloseable { ZipFile zip ->
            ZipEntry entry = zip.getEntry(GRADLE_BUILD_RECEIPT)
            if (entry == null) {
                throw new IllegalArgumentException("'${jar}' contains no ${GRADLE_BUILD_RECEIPT}, so it was not published from gradle/gradle")
            }
            zip.getInputStream(entry).withCloseable { receipt.load(it) }
        }
        receipt
    }

    /**
     * Converts a Gradle build receipt timestamp into an OSGi version qualifier.
     * <p/>
     * For example {@code 20240711143741+0000} becomes {@code v20240711-1437}, which is the qualifier format Eclipse
     * conventionally uses. The seconds and the UTC offset are dropped because the receipt always records UTC and a
     * minute is precise enough to identify a release.
     *
     * @param buildTimestamp the {@code buildTimestamp} value of a Gradle build receipt
     * @return the OSGi version qualifier
     */
    static String versionQualifierFor(String buildTimestamp) {
        def matcher = buildTimestamp =~ /^(\d{8})(\d{4})\d{2}[+-]\d{4}$/
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unexpected Gradle build receipt timestamp: '${buildTimestamp}'")
        }
        "v${matcher.group(1)}-${matcher.group(2)}"
    }

    static File firstDependencyJar(Configuration configuration) {
        ResolvedArtifact jarArtifact = findJarArtifact(getResolvedDependency(configuration))
        jarArtifact.file
    }

    static File firstDependencySourceJar(Project project, Configuration configuration) {
        ResolvedArtifact jarArtifact = findSourceJarArtifact(getResolvedSourceDependency(project, configuration))
        jarArtifact.file
    }

    private static ResolvedDependency getResolvedDependency(Configuration configuration) {
        configuration.resolvedConfiguration.firstLevelModuleDependencies.first()
    }

    private static ResolvedDependency getResolvedSourceDependency(Project project, Configuration configuration) {
        def dep = configuration.incoming.dependencies.collect {"$it.group:$it.name:$it.version:sources" }.first()
        project.configurations.detachedConfiguration([project.dependencies.create(dep)] as Dependency[]).resolvedConfiguration.getFirstLevelModuleDependencies().first()
    }

    private static ResolvedArtifact findJarArtifact(ResolvedDependency dependency) {
        dependency.moduleArtifacts.find { it.extension == 'jar' }
    }

    private static ResolvedArtifact findSourceJarArtifact(ResolvedDependency dependency) {
        dependency.moduleArtifacts.find { it.classifier == 'sources' }
    }

    static String manifestContent(Iterable<File> jars, String template, String packageFilter, String bundleVersion, String qualifier, String sourceReference = null) {
        List<String> packageNames = packageNames(jars, packageFilter) as List
        packageNames.sort()
        String fullVersion = "${bundleVersion}.${qualifier}"
        manifestFor(template, packageNames, bundleVersion, fullVersion, sourceReference)
    }

    private static Set<String> packageNames(Iterable<File> jars, String filteredPackagesPattern) {
        def result = [] as Set
        Pattern filteredPackages = Pattern.compile(filteredPackagesPattern)
        jars.each { jar->
            new ZipInputStream(new FileInputStream(jar)).withCloseable { zip ->
                ZipEntry e
                while (e = zip.nextEntry) {
                    if (!e.directory && e.name.endsWith(".class")) {
                        int index = e.name.lastIndexOf('/')
                        if (index < 0) index = e.name.length()
                        String packageName = e.name.substring(0, index).replace('/', '.')
                        if (!packageName.matches(filteredPackages)) {
                            result.add(packageName)
                        }
                    }
                }
            }
        }
        result
    }

    private static String manifestFor(String manifestTemplate, Collection<String> packageNames, String mainVersion, String fullVersion, String sourceReference) {
        StringBuilder manifest = new StringBuilder(manifestTemplate)

        if (!packageNames.isEmpty()) {
            String exportedPackages = packageNames.collect { " ${it};version=\"${mainVersion}\"" }.join(',\n')
            manifest.append "Export-Package:${exportedPackages}\n"
        }
        manifest.append "Bundle-Version: $fullVersion\n"
        if (sourceReference) {
            manifest.append "Eclipse-SourceReferences: $sourceReference\n"
        }
        manifest.toString()
    }
}
