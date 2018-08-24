package org.eclipse.buildship.ui.view.launch

import org.eclipse.debug.core.ILaunchConfiguration

import org.eclipse.buildship.core.CorePlugin
import org.eclipse.buildship.core.GradleDistribution
import org.eclipse.buildship.core.launch.GradleRunConfigurationAttributes
import org.eclipse.buildship.ui.test.fixtures.ProjectSynchronizationSpecification

class GradleLaunchConfigurationTest extends ProjectSynchronizationSpecification {

    def "Launch configuration can be loaded and validated after the project is deleted"() {
        setup:
        def project = dir("root") {
            file 'build.gradle'
            file 'settings.gradle'
        }
        importAndWait(project)
        def attributes = attributes('${workspace_loc:/root}')
        ILaunchConfiguration configuration = CorePlugin.gradleLaunchConfigurationManager().getOrCreateRunConfiguration(attributes)

        when:
        deleteAllProjects(true)
        attributes = GradleRunConfigurationAttributes.from(configuration)
        attributes.getWorkingDirExpression()

        then:
        notThrown(Exception)

        when:
        attributes.getWorkingDir()

        then:
        thrown(Exception)
    }

    private GradleRunConfigurationAttributes attributes(String projectLoc) {
        new GradleRunConfigurationAttributes([],
            projectLoc,
            GradleDistribution.fromBuild().distributionInfo.serializeToString(),
            "",
            null,
            [],
            [],
            true,
            true,
            false,
            false,
            false);
    }

}
