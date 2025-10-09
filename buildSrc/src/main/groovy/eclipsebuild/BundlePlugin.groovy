/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Donát Csikós (Gradle Inc.) - initial API and implementation and initial documentation
 */

package eclipsebuild

import groovy.xml.XmlParser
import groovy.xml.XmlNodePrinter

import org.eclipse.osgi.framework.util.Headers
import org.eclipse.osgi.internal.resolver.StateObjectFactoryImpl
import org.eclipse.osgi.internal.resolver.UserState
import org.eclipse.osgi.service.resolver.BundleSpecification
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.tasks.Copy
import org.gradle.jvm.tasks.Jar
import org.osgi.framework.Version
import org.osgi.framework.VersionRange

/**
 * Gradle plug-in for building Eclipse bundles.
 * <p/>
 * It adds extra functionality to the existing Java plug-in to resolve dependencies and Eclipse
 * bundles much like with PDE-based build.
 * <p/>
 * It makes OSGi-related variables like os, ws and arch available for the project (via project.ext)
 * for building against platform-dependent dependencies. An example dependency on SWT:
 * <pre>
 * implementation "eclipse:org.eclipse.swt.${ECLIPSE_WS}.${ECLIPSE_OS}.${ECLIPSE_ARCH}:+"
 * </pre>
 * A {@code withEclipseBundle} method is declared that can use the target platform's version mapping and fix
 * the dependency version to a concrete value. For example:
 * <pre>
 * implementation withDependencies("org.eclipse.swt.${ECLIPSE_WS}.${ECLIPSE_OS}.${ECLIPSE_ARCH}")
 * </pre>
 * To construct the output jar the plugin loads the contents of the <code>build.properties</code>
 * file and sync it with the jar's content.
 * <p/>
 * The plug-in defines a new scope called {@code bundled}. If dependency is defined with this
 * scope and the {@code updateLibs} task is called, the dependency (and its transitives) is (1)
 * copied into the lib folder (2) added to the project descriptors and (3) referenced from the
 * bundle manifest file.
 */
class BundlePlugin implements Plugin<Project> {

    static final String TASK_NAME_UPDATE_LIBS = 'updateLibs'
    static final String TASK_NAME_COPY_LIBS = 'copyLibs'
    static final String TASK_NAME_UPDATE_MANIFEST = 'updateManifest'

    @Override
    public void apply(Project project) {
        configureProject(project)
        loadDependenciesFromManifest(project)

        addTaskCopyLibs(project)
        addTaskUpdateManifest(project)
        addTaskUpdateLibs(project)
    }

    static void configureProject(Project project) {
        // apply the java plugin
        project.plugins.apply(JavaLibraryPlugin)

        // make new variables for the build.gradle file e.g. for platform-dependent dependencies
        Constants.exposePublicConstantsFor(project)

        // add new configuration scope
        project.configurations.create('bundled')
        project.configurations.create('bundledSource')
        project.configurations { api.extendsFrom bundled }

        // make the most basic task depend on the target platform assembling
        project.tasks.compileJava.dependsOn  ":${BuildDefinitionPlugin.TASK_NAME_INSTALL_TARGET_PLATFORM}"

        // make sure the required descriptors exist
        assert project.file('build.properties').exists()
        assert project.file('META-INF/MANIFEST.MF').exists()

        // add sources jar task
        project.task('sourcesJar', type: Jar, dependsOn: 'classes') {
            archiveClassifier = 'sources'
            from project.sourceSets.main.allSource
        }
        project.artifacts { archives project.tasks.sourcesJar }

        // use the same MANIFEST.MF file as it is in the project except the Bundle-Version
        PluginUtils.updatePluginManifest(project)

        // parse build.properties and sync it with output jar
        PluginUtils.configurePluginJarInput(project)
    }

    static void loadDependenciesFromManifest(Project project) {
        // obtain BundleDescription class from OSGi to have precise dependency definitions
        def manifest = Headers.parseManifest(new FileInputStream(project.file('META-INF/MANIFEST.MF')))
        def factory = new StateObjectFactoryImpl()
        def description = factory.createBundleDescription(new UserState(), manifest, null, 1)
        description.requiredBundles.each { defineDependency(it, project) }
    }

    static void defineDependency(BundleSpecification requiredBundle, Project project) {
        def pluginName = requiredBundle.getName()
        def versionRange = requiredBundle.versionRange
        checkMappedVersion(versionRange, pluginName, project)

        // handle dependencies to local projects
        Project localProject = project.rootProject.allprojects.find { it.name == pluginName }
        if (localProject && versionRange.includes(new Version(localProject.version))) {
            project.dependencies.api(localProject)
            return
        }

        // handle dependencies to target platform bundles
        def left = versionRange.left
        def right = versionRange.right
        def unbound = new Version(0, 0, 0)

        String dependency
        if (left == unbound && right == null) {
            // no version constraint defined in the manifest file
            dependency = DependencyUtils.calculatePluginDependency(project, pluginName)
        } else if (left.compareTo(unbound) > 0 && right == null) {
            // simple minimum version constraint defined in the manifest file, e.g. bundle-version="0.3.0"
            dependency = DependencyUtils.calculatePluginDependency(project, pluginName, left.toString())
        } else if (left.compareTo(unbound) > 0 && right != null && versionRange.includeMinimum && !versionRange.includeMaximum) {
            // version range defined with inclusive left and exclusive right version constraint, e.g. bundle-version="[1.2.1,2.0.0)"
            dependency = DependencyUtils.calculatePluginDependency(project, pluginName, left.toString())
        } else {
            // otherwise fail the build
            throw new GradleException("Unsupported dependency version constraint '${versionRange}' for dependency ${pluginName}.")
        }
        project.dependencies.api(dependency)
        project.logger.info("Dependency defined in MANIFEST.MF: ${dependency}")
    }

    private static void checkMappedVersion(VersionRange versionRange, String pluginName, Project project) {
        def mappedVersion = DependencyUtils.mappedVersion(project, pluginName)
        if (mappedVersion) {
            def version = new Version(mappedVersion)
            if (!versionRange.includes(version)) {
                throw new GradleException("Mapped version '$mappedVersion' for dependency '$pluginName' does not match version range constraint '$versionRange' defined in MANIFEST.MF.")
            }
        }
    }

    static void addTaskCopyLibs(Project project) {
        project.task(TASK_NAME_COPY_LIBS,  dependsOn: [
            project.configurations.bundled,
            project.configurations.bundledSource,
            ":${BuildDefinitionPlugin.TASK_NAME_INSTALL_TARGET_PLATFORM}"
        ], type: Copy) {
            group = Constants.gradleTaskGroupName
            description = 'Copies the bundled dependencies into the lib folder.'

            def libDir = project.file('lib')

            // before the update delete all the libraries that are currently in the lib folder
            doFirst {
                libDir.listFiles().each { File f ->
                    if (f.toString().endsWith('.jar')) {
                        logger.info("Deleting ${f.name}")
                        f.delete()
                    }
                }
            }

            // copy the dependencies to the 'libs' folder
            into libDir
            from project.configurations.bundled
        }
    }

    static void addTaskUpdateManifest(Project project) {
        project.task(TASK_NAME_UPDATE_MANIFEST, dependsOn: project.configurations.bundled) {
            group = Constants.gradleTaskGroupName
            description = 'Updates the manifest file with the bundled dependencies.'
            doLast { updateManifest(project) }
        }
    }

    static void updateManifest(Project project) {
        // don't write anything if there is no bundled dependency
        if (project.configurations.bundled.dependencies.isEmpty()) {
            return
        }

        File manifest = project.file('META-INF/MANIFEST.MF')
        project.logger.info("Update project manifest '${manifest.absolutePath}'")
        List<String> lines = manifest.readLines()
        int i = 0

        manifest.withPrintWriter { out ->
            // copy file upto line with 'Bundle-ClassPath: .'
            while (i < lines.size() && !lines[i].startsWith('Bundle-ClassPath: .,')) {
                out.println(lines[i])
                i++
            }

            out.print 'Bundle-ClassPath: .,'

            // add a sorted list of jar file names under the Bundle-Classpath section
            boolean comma = false
            def bundledConfig = project.configurations.bundled as List
            bundledConfig.sort { it.name }.each { File jarFile ->
                if (jarFile.toString().endsWith('.jar')) {
                    if (comma) {
                        out.println(',')
                    } else {
                        out.println()
                    }
                    String name = jarFile.getName()
                    out.print(" lib/$name")
                    comma = true
                }
            }
            out.println()

            // skip lines up to 'Export-Package: '
            while (i < lines.size() && !lines[i].startsWith('Export-Package: ')) {
                i++
            }

            // copy the remaining lines
            while (i < lines.size()) {
                out.println lines[i]
                i++
            }
        }
        project.logger.debug("Manifest content:\n${manifest.text}")


        // update the .classpath file
        def classpathFile = project.file('.classpath')
        project.logger.info("Update .classpath file '${classpathFile.absolutePath}'")
        def classpathXml = new XmlParser().parse(classpathFile)
        // delete all nodes pointing to the lib folder
        classpathXml.findAll { it.name().equals('classpathentry') && it.@path.startsWith('lib/') }.each { classpathXml.remove(it) }
        // re-create the deleted nodes with the 'sourcepath' attribute
        project.configurations.bundled.sort { it.name }.each { File jarFile ->
            def name = jarFile.getName()
            def nameWithoutExtension = name.substring(0, name.lastIndexOf('.'))
            new Node(classpathXml, 'classpathentry', ['exported' : 'true', 'kind' : 'lib',  'path' : "lib/$name", 'sourcepath' : "lib/${nameWithoutExtension}-sources.jar"])
        }
        new XmlNodePrinter(new PrintWriter(new FileWriter(classpathFile))).print(classpathXml)
        project.logger.debug(".classpath content:\n${classpathFile.text}")
    }

    static void addTaskUpdateLibs(Project project) {
        project.task(TASK_NAME_UPDATE_LIBS, dependsOn: [
            TASK_NAME_COPY_LIBS,
            TASK_NAME_UPDATE_MANIFEST
        ]) {
            group = Constants.gradleTaskGroupName
            description = 'Copies the bundled dependencies into the lib folder and updates the manifest file.'
        }
    }

}
