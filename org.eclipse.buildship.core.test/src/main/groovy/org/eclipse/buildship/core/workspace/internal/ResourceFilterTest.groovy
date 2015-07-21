package org.eclipse.buildship.core.workspace.internal

import org.eclipse.buildship.core.CorePlugin
import org.eclipse.buildship.core.util.file.FileUtils;

import org.eclipse.core.filesystem.EFS
import org.eclipse.core.resources.IFolder
import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IProjectDescription
import org.eclipse.core.resources.IWorkspace
import org.eclipse.core.runtime.NullProgressMonitor

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class ResourceFilterTest extends Specification {

    @Rule
    TemporaryFolder tempFolder

    IProject project

    def setup() {
        // create an empty sample project for all tests
        IProjectDescription projectDescription = workspace().newProjectDescription("project")
        project = workspace().getRoot().getProject("project")
        project.create(projectDescription, new NullProgressMonitor())
        project.open(new NullProgressMonitor())
    }

    def cleanup() {
        project.delete(true, null)
        CorePlugin.workspaceOperations().deleteAllProjects(null)
    }

    def "Define resource filter on the project"() {
        given:
        projectFolder('filtered')
        projectFolder('unfiltered')

        expect:
        project.getFolder('filtered').exists()
        project.getFolder('unfiltered').exists()
        workspace().validateFiltered(project.getFolder('filtered')).isOK()
        workspace().validateFiltered(project.getFolder('unfiltered')).isOK()

        when:
        ResourceFilter.attachFilters(project, [ toFile(project.getFolder('filtered')) ], null)

        then:
        !workspace().validateFiltered(project.getFolder('filtered')).isOK()
        workspace().validateFiltered(project.getFolder('unfiltered')).isOK()
    }

    def "Define resource filter on a subfolder"() {
        given:
        projectFolder('basefolder/subfolder')

        expect:
        project.getFolder('basefolder/subfolder').exists()
        workspace().validateFiltered(project.getFolder('basefolder/subfolder')).isOK()

        when:
        ResourceFilter.attachFilters(project, [ toFile(project.getFolder('basefolder/subfolder')) ], null)

        then:
        workspace().validateFiltered(project.getFolder('basefolder')).isOK()
        !workspace().validateFiltered(project.getFolder('basefolder/subfolder')).isOK()
    }

    def "Defining resource filter on direct child folder does not hide anything in inner folder structure"() {
        given:
        projectFolder('pkg')
        projectFolder('src/main/java/pkg')

        when:
        ResourceFilter.attachFilters(project, [toFile(project.getFolder('pkg'))], null)

        then:
        !workspace().validateFiltered(project.getFolder('pkg')).isOK()
        workspace().validateFiltered(project.getFolder('src/main/java/pkg')).isOK()
    }

    def "Defining resource filter on non-child location is ignored"() {
        given:
        ResourceFilter.attachFilters(project, [tempFolder.newFolder('siblingproject')], null)

        expect:
        project.getFilters().length == 0
    }


    def "Defining new resource filter preserves the previously defined resource filters"() {
        given:
        projectFolder('alpha')
        projectFolder('beta')

        when:
        ResourceFilter.attachFilters(project, [toFile(project.getFolder('alpha'))], null)

        then:
        project.getFilters().length == 1
        (project.getFilters()[0].getFileInfoMatcherDescription().getArguments() as String).endsWith('alpha')

        when:
        ResourceFilter.attachFilters(project, [toFile(project.getFolder('beta'))], null)

        then:
        project.getFilters().length == 2
        (project.getFilters()[0].getFileInfoMatcherDescription().getArguments() as String).endsWith('alpha')
        (project.getFilters()[1].getFileInfoMatcherDescription().getArguments() as String).endsWith('beta')
    }

    def "Defining new resource filter is idempotent"() {
        given:
        projectFolder('alpha')

        expect:
        project.getFilters().length == 0

        when:
        ResourceFilter.attachFilters(project, [toFile(project.getFolder('alpha'))], null)

        then:
        project.getFilters().length == 1
        (project.getFilters()[0].getFileInfoMatcherDescription().getArguments() as String).endsWith('alpha')

        when:
        ResourceFilter.attachFilters(project, [toFile(project.getFolder('alpha'))], null)

        then:
        project.getFilters().length == 1
        (project.getFilters()[0].getFileInfoMatcherDescription().getArguments() as String).endsWith('alpha')
    }

    private def projectFolder(String path) {
        FileUtils.ensureFolderHierarchyExists(project.getFolder(path))
    }

    private static IWorkspace workspace() {
        LegacyEclipseSpockTestHelper.getWorkspace()
    }

    private static def toFile(IFolder folder) {
        def uri = folder.getLocationURI()
        EFS.getStore(uri).toLocalFile(0, new NullProgressMonitor())
    }

}
