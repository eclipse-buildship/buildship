package org.eclipse.buildship.core.workspace.internal

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import com.gradleware.tooling.toolingmodel.OmniEclipseSourceDirectory

import org.eclipse.core.resources.IFolder
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jdt.core.IClasspathAttribute
import org.eclipse.jdt.core.IClasspathEntry
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.JavaCore
import org.eclipse.core.runtime.Path

import org.eclipse.buildship.core.CorePlugin
import org.eclipse.buildship.core.util.file.FileUtils

class SourceFolderUpdaterTest extends Specification {

    @Rule
    TemporaryFolder tempFolder

    def cleanup() {
        CorePlugin.workspaceOperations().deleteAllProjects(new NullProgressMonitor())
    }

    def "Model source folders are added"() {
        given:
        def project = javaProject('name': 'project-name', 'model-source-folders': [], 'manual-source-folders': [])
        def newModelSourceFolders = gradleSourceFolders(['src'])

        expect:
        project.rawClasspath.length == 0

        when:
        SourceFolderUpdater.update(project, newModelSourceFolders, null)

        then:
        project.rawClasspath.length == 1
        project.rawClasspath[0].entryKind == IClasspathEntry.CPE_SOURCE
        project.rawClasspath[0].path.toPortableString() == "/project-name/src"
        project.rawClasspath[0].extraAttributes.length == 1
        project.rawClasspath[0].extraAttributes[0].name == SourceFolderUpdater.CLASSPATH_ATTRIBUTE_FROM_GRADLE_MODEL

    }

    def "Duplicate model source folders are merged into one source entry"() {
        given:
        def project = javaProject('name': 'project-name', 'model-source-folders': [], 'manual-source-folders': [])
        def newModelSourceFolders = gradleSourceFolders(['src', 'src'])

        when:
        SourceFolderUpdater.update(project, newModelSourceFolders, null)

        then:
        project.rawClasspath.length == 1
        project.rawClasspath[0].entryKind == IClasspathEntry.CPE_SOURCE
        project.rawClasspath[0].path.toPortableString() == "/project-name/src"
        project.rawClasspath[0].extraAttributes.length == 1
        project.rawClasspath[0].extraAttributes[0].name == SourceFolderUpdater.CLASSPATH_ATTRIBUTE_FROM_GRADLE_MODEL
    }

    def "Previous model source folders are removed if they no longer exist in the Gradle model"() {
        given:
        def project = javaProject('name': 'project-name', 'model-source-folders': ['src-old'], 'manual-source-folders': [])
        def newModelSourceFolders = gradleSourceFolders(['src-new'])

        when:
        SourceFolderUpdater.update(project, newModelSourceFolders, null)

        then:
        project.rawClasspath.length == 1
        project.rawClasspath[0].entryKind == IClasspathEntry.CPE_SOURCE
        project.rawClasspath[0].path.toPortableString() == "/project-name/src-new"
        project.rawClasspath[0].extraAttributes.length == 1
        project.rawClasspath[0].extraAttributes[0].name == SourceFolderUpdater.CLASSPATH_ATTRIBUTE_FROM_GRADLE_MODEL
    }

    def "Non-model source folders are preserved even if they are not part of the Gradle model"() {
        given:
        def project = javaProject('name': 'project-name', 'model-source-folders': [], 'manual-source-folders': ['src'])
        def newModelSourceFolders = gradleSourceFolders(['src-gradle'])

        expect:
        project.rawClasspath.length == 1

        when:
        SourceFolderUpdater.update(project, newModelSourceFolders, null)

        then:
        project.rawClasspath.length == 2
        project.rawClasspath[0].entryKind == IClasspathEntry.CPE_SOURCE
        project.rawClasspath[0].path.toPortableString() == "/project-name/src-gradle"
        project.rawClasspath[0].extraAttributes.length == 1
        project.rawClasspath[0].extraAttributes[0].name == SourceFolderUpdater.CLASSPATH_ATTRIBUTE_FROM_GRADLE_MODEL
        project.rawClasspath[1].entryKind == IClasspathEntry.CPE_SOURCE
        project.rawClasspath[1].path.toPortableString() == "/project-name/src"
        project.rawClasspath[1].extraAttributes.length == 0
    }

    def "Model source folders that were previously defined manually are transformed to model source folders"() {
        given:
        def project = javaProject('name': 'project-name', 'model-source-folders': [], 'manual-source-folders': ['src'])
        def newModelSourceFolders = gradleSourceFolders(['src'])

        expect:
        project.rawClasspath.length == 1
        project.rawClasspath[0].path.toPortableString() == "/project-name/src"
        project.rawClasspath[0].extraAttributes.length == 0

        when:
        SourceFolderUpdater.update(project, newModelSourceFolders, null)

        then:
        project.rawClasspath.length == 1
        project.rawClasspath[0].entryKind == IClasspathEntry.CPE_SOURCE
        project.rawClasspath[0].path.toPortableString() == "/project-name/src"
        project.rawClasspath[0].extraAttributes.length == 1
        project.rawClasspath[0].extraAttributes[0].name == SourceFolderUpdater.CLASSPATH_ATTRIBUTE_FROM_GRADLE_MODEL
    }

    def "Classpath inclusion/exclusion patterns on Gradle source folders are preserved"() {
        given:
        def project = javaProject('name': 'project-name', 'model-source-folders': ['src'], 'manual-source-folders': [], 'inclusion-pattern': (IPath[])[new Path("manual-inclusion-pattern")], 'exclusion-pattern': (IPath[])[new Path("manual-exclusion-pattern")])
        def newModelSourceFolders = gradleSourceFolders(['src'])

        when:
        SourceFolderUpdater.update(project, newModelSourceFolders, null)

        then:
        project.rawClasspath[0].getInclusionPatterns()[0].toString() == "manual-inclusion-pattern"
        project.rawClasspath[0].getExclusionPatterns()[0].toString() == "manual-exclusion-pattern"
    }

    private List<OmniEclipseSourceDirectory> gradleSourceFolders(List<String> folderPaths) {
        folderPaths.collect { String folderPath ->
            def sourceDirectory = Mock(OmniEclipseSourceDirectory)
            sourceDirectory.getPath() >> folderPath
            sourceDirectory
        }
    }

    private IJavaProject javaProject(HashMap arguments) {
        def projectName = arguments['name']
        def modelSourceFolders = arguments['model-source-folders']
        def manualSourceFolders = arguments['manual-source-folders']
        def inclusionPattern = arguments['inclusion-pattern']?:new IPath[0]
        def exclusionPattern = arguments['exclusion-pattern']?:new IPath[0]

        // create project folder
        def location = tempFolder.newFolder(projectName)

        // create project
        def project = CorePlugin.workspaceOperations().createProject('project-name', location, [], [], new NullProgressMonitor())
        def description = project.getDescription()
        description.setNatureIds([JavaCore.NATURE_ID] as String[])
        project.setDescription(description, new NullProgressMonitor())

        // convert it to a java project
        def javaProject = JavaCore.create(project)
        IFolder outputLocation = project.getFolder('bin')
        FileUtils.ensureFolderHierarchyExists(outputLocation)
        javaProject.setOutputLocation(outputLocation.getFullPath(), new NullProgressMonitor())

        // define source classpath
        def manualSourceEntries = manualSourceFolders.collect { String path ->
            def folder = javaProject.project.getFolder(path)
            FileUtils.ensureFolderHierarchyExists(folder)
            def root = javaProject.getPackageFragmentRoot(folder)
            JavaCore.newSourceEntry(root.path)
        }
        def modelSourceEntries = modelSourceFolders.collect { String path ->
            def folder = javaProject.project.getFolder(path)
            FileUtils.ensureFolderHierarchyExists(folder)
            def root = javaProject.getPackageFragmentRoot(folder)
            def attribute = JavaCore.newClasspathAttribute(SourceFolderUpdater.CLASSPATH_ATTRIBUTE_FROM_GRADLE_MODEL, "true")
            JavaCore.newSourceEntry(root.path, inclusionPattern, exclusionPattern, null, [attribute] as IClasspathAttribute[])
        }
        javaProject.setRawClasspath(manualSourceEntries + modelSourceEntries as IClasspathEntry[], new NullProgressMonitor())

        // return the created instance
        javaProject
    }

}
