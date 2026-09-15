package eclipsebuild

import spock.lang.Specification
import spock.lang.TempDir

class AssembleTargetPlatformTaskTest extends Specification {

    private static final String REMOTE_DEFINITION = '''\
        <?xml version="1.0" encoding="UTF-8" standalone="no"?>
        <target name="Test" sequenceNumber="1">
            <locations>
                <location includeAllPlatforms="false" type="InstallableUnit">
                    <unit id="org.eclipse.sdk.ide" version="4.34.0.I20241120-1800"/>
                    <repository location="https://download.eclipse.org/releases/2024-12"/>
                </location>
            </locations>
        </target>
        '''.stripIndent()

    @TempDir
    File tempDir

    def "the project location token is the placeholder a target definition writes"() {
        expect:
        AssembleTargetPlatformTask.PROJECT_LOCATION_TOKEN == '${project_loc}'
    }

    def "a definition reading only remote update sites can be cached"() {
        expect:
        !AssembleTargetPlatformTask.readsProjectLocalRepository(targetDefinition(REMOTE_DEFINITION))
    }

    def "a definition reading a repository inside the project cannot be cached"() {
        given:
        String definition = REMOTE_DEFINITION.replace(
                'https://download.eclipse.org/releases/2024-12',
                '${project_loc}/repository')

        expect:
        AssembleTargetPlatformTask.readsProjectLocalRepository(targetDefinition(definition))
    }

    private File targetDefinition(String content) {
        File definitionFile = new File(tempDir, 'test.target')
        definitionFile.text = content
        definitionFile
    }
}
