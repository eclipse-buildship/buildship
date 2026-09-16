package eclipsebuild.jar

import spock.lang.Specification
import spock.lang.TempDir

import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class JarBundleUtilsTest extends Specification {

    @TempDir
    File tempDir

    def "reads the build receipt of a jar published from gradle/gradle"() {
        given:
        File jar = jarContaining('org/gradle/build-receipt.properties', '''\
            baseVersion=8.9
            buildTimestamp=20240711143741+0000
            commitId=d536ef36a19186ccc596d8817123e5445f30fef8
            isSnapshot=false
            versionNumber=8.9
            '''.stripIndent())

        when:
        Properties receipt = JarBundleUtils.gradleBuildReceipt(jar)

        then:
        receipt.getProperty('buildTimestamp') == '20240711143741+0000'
        receipt.getProperty('commitId') == 'd536ef36a19186ccc596d8817123e5445f30fef8'
        receipt.getProperty('versionNumber') == '8.9'
    }

    def "fails with a helpful message when the jar has no build receipt"() {
        given:
        File jar = jarContaining('org/example/Thing.class', 'not a receipt')

        when:
        JarBundleUtils.gradleBuildReceipt(jar)

        then:
        IllegalArgumentException e = thrown()
        e.message.contains('org/gradle/build-receipt.properties')
        e.message.contains('gradle/gradle')
    }

    def "converts a build receipt timestamp into an OSGi version qualifier"() {
        expect:
        JarBundleUtils.versionQualifierFor(buildTimestamp) == qualifier

        where:
        buildTimestamp          | qualifier
        '20240711143741+0000'   | 'v20240711-1437'
        '20260819141609+0000'   | 'v20260819-1416'
        '20240101000000+0000'   | 'v20240101-0000'
        '20240711143741-0700'   | 'v20240711-1437'
    }

    def "rejects a timestamp it does not recognise"() {
        when:
        JarBundleUtils.versionQualifierFor(buildTimestamp)

        then:
        IllegalArgumentException e = thrown()
        e.message.contains(buildTimestamp)

        where:
        buildTimestamp << ['', '20240711', '2024-07-11 14:37:41 UTC', '20240711143741']
    }

    def "the qualifier is stable for a given Tooling API version"() {
        given:
        String receipt = 'buildTimestamp=20240711143741+0000\ncommitId=d536ef36a19186ccc596d8817123e5445f30fef8\n'

        when:
        File first = jarContaining('org/gradle/build-receipt.properties', receipt, 'first.jar')
        File second = jarContaining('org/gradle/build-receipt.properties', receipt, 'second.jar')

        then:
        JarBundleUtils.versionQualifierFor(JarBundleUtils.gradleBuildReceipt(first).getProperty('buildTimestamp')) ==
                JarBundleUtils.versionQualifierFor(JarBundleUtils.gradleBuildReceipt(second).getProperty('buildTimestamp'))
    }

    private File jarContaining(String entryName, String content, String jarName = 'test.jar') {
        File jar = new File(tempDir, jarName)
        new ZipOutputStream(new FileOutputStream(jar)).withCloseable { zip ->
            zip.putNextEntry(new ZipEntry(entryName))
            zip.write(content.getBytes('UTF-8'))
            zip.closeEntry()
        }
        jar
    }
}
