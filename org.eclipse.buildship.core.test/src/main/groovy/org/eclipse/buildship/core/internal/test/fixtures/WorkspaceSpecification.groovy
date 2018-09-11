/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Etienne Studer & Donát Csikós (Gradle Inc.) - initial API and implementation and initial documentation
 */

package org.eclipse.buildship.core.internal.test.fixtures

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.AutoCleanup
import spock.lang.Specification

import com.google.common.collect.ImmutableList
import com.google.common.io.Files

import org.eclipse.core.resources.IMarker
import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IResource
import org.eclipse.core.resources.IWorkspace
import org.eclipse.core.runtime.CoreException
import org.eclipse.core.runtime.ILogListener
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Path
import org.eclipse.core.runtime.Platform
import org.eclipse.debug.core.DebugPlugin
import org.eclipse.debug.core.ILaunchConfigurationType
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy
import org.eclipse.debug.core.ILaunchManager
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.JavaCore

import org.eclipse.buildship.core.*
import org.eclipse.buildship.core.internal.CorePlugin
import org.eclipse.buildship.core.internal.configuration.BuildConfiguration
import org.eclipse.buildship.core.internal.configuration.ConfigurationManager
import org.eclipse.buildship.core.internal.launch.GradleRunConfigurationDelegate
import org.eclipse.buildship.core.internal.marker.GradleErrorMarker
import org.eclipse.buildship.core.internal.preferences.DefaultPersistentModel
import org.eclipse.buildship.core.internal.preferences.PersistentModel
import org.eclipse.buildship.core.internal.workspace.PersistentModelBuilder
import org.eclipse.buildship.core.internal.workspace.WorkspaceOperations

/**
 * Base Spock test specification to verify Buildship functionality against the current state of the
 * workspace.
 */
abstract class WorkspaceSpecification extends Specification {

    @Rule
    TemporaryFolder tempFolderProvider

    @AutoCleanup
    TestEnvironment environment = TestEnvironment.INSTANCE

    private File externalTestDir

    private LogCollector logCollector = new LogCollector()

    def setup() {
        externalTestDir = tempFolderProvider.newFolder('external')
        Platform.addLogListener(logCollector)
    }

    def cleanup() {
        Platform.removeLogListener(logCollector)
        logCollector.clear()
        DebugPlugin.default.launchManager.launchConfigurations.each { it.delete() }
        deleteAllProjects(true)
        deleteAllGradleErrorMarkers()
    }

    protected void deleteAllProjects(boolean includingContent) {
        for (IProject project : CorePlugin.workspaceOperations().allProjects) {
             try {
                project.delete(includingContent, true, null)
            } catch(CoreException e) {
               CorePlugin.logger().warn("Cannot delete test project ${project.name}; falling back to workspace cleaning", e)
               project.delete(false, true, null)
            }
        }
        workspaceDir.listFiles().findAll { it.isDirectory() && it.name != '.metadata' }.each { File it -> it.deleteDir() }
    }

    protected <T> void registerService(Class<T> serviceType, T implementation) {
        environment.registerService(serviceType, implementation)
    }

    protected FileTreeBuilder fileTree(File baseDir) {
        new FileTreeBuilder(baseDir)
    }

    protected File fileTree(File baseDir, @DelegatesTo(value = FileTreeBuilder, strategy = Closure.DELEGATE_FIRST) Closure config) {
        fileTree(baseDir).call(config)
    }

    protected File dir(String location) {
        def dir = getDir(location)
        dir.mkdirs()
        return dir
    }

    protected File dir(String location, @DelegatesTo(value = FileTreeBuilder, strategy = Closure.DELEGATE_FIRST) Closure config) {
        return fileTree(dir(location), config)
    }

    protected File getDir(String location) {
        return new File(testDir, location)
    }

    protected File getTestDir() {
        externalTestDir
    }

    protected File file(String location) {
        def file = getFile(location)
        Files.touch(file)
        return file
    }

    protected File getFile(String location) {
        return new File(testDir, location)
    }


    protected File workspaceDir(String location) {
        def dir = getWorkspaceDir(location)
        dir.mkdirs()
        return dir
    }

    protected File workspaceDir(String location, @DelegatesTo(value = FileTreeBuilder, strategy = Closure.DELEGATE_FIRST) Closure config) {
        return fileTree(workspaceDir(location), config)
    }

    protected File getWorkspaceDir(String location) {
        return new File(workspaceDir, location)
    }

    protected IWorkspace getWorkspace() {
        LegacyEclipseSpockTestHelper.workspace
    }

    protected File getWorkspaceDir() {
        workspace.root.location.toFile()
    }

    protected File workspaceFile(String location) {
        def file = getWorkspaceFile(location)
        Files.touch(file)
        return file
    }

    protected File getWorkspaceFile(String location) {
        return new File(workspaceDir, location)
    }

    protected IProject newClosedProject(String name) {
        EclipseProjects.newClosedProject(name, dir(name))
    }

    protected IProject newProject(String name) {
        EclipseProjects.newProject(name, dir(name))
    }

    protected IJavaProject newJavaProject(String name) {
        EclipseProjects.newJavaProject(name, dir(name))
    }

    protected List<IProject> allProjects() {
        workspace.root.projects
    }

    protected IProject findProject(String name) {
        CorePlugin.workspaceOperations().findProjectByName(name).orNull()
    }

    protected IJavaProject findJavaProject(String name) {
        IProject project = findProject(name)
        return project == null ? null : JavaCore.create(project)
    }

    protected WorkspaceOperations getWorkspaceOperations() {
        CorePlugin.workspaceOperations()
    }

    protected ConfigurationManager getConfigurationManager() {
        CorePlugin.configurationManager()
    }

    protected BuildConfiguration createInheritingBuildConfiguration(File projectDir) {
        configurationManager.createBuildConfiguration(projectDir, false, GradleDistributions.fromBuild(), null, false, false, false)
    }

    protected BuildConfiguration createOverridingBuildConfiguration(File projectDir, GradleDistribution distribution = GradleDistributions.fromBuild(),
                                                                  boolean buildScansEnabled = false, boolean offlineMode = false, boolean autoSync = false, File gradleUserHome = null) {
        configurationManager.createBuildConfiguration(projectDir, true, distribution, gradleUserHome, buildScansEnabled, offlineMode, autoSync)
    }

    protected PersistentModelBuilder persistentModelBuilder(PersistentModel model) {
        new PersistentModelBuilder(model)
    }

    protected PersistentModelBuilder persistentModelBuilder(IProject project) {
        new PersistentModelBuilder(emptyPersistentModel(project))
    }

    protected PersistentModel emptyPersistentModel(IProject project) {
        new DefaultPersistentModel(project, new Path("build"), new Path("build.gradle"), [], [], [], [], [], [])
    }

    protected ILaunchConfigurationWorkingCopy createLaunchConfig(String id, String name = 'launch-config') {
        ILaunchManager launchManager = DebugPlugin.default.launchManager
        ILaunchConfigurationType type = launchManager.getLaunchConfigurationType(id)
        type.newInstance(null, launchManager.generateLaunchConfigurationName(name))
    }

    protected ILaunchConfigurationWorkingCopy createGradleLaunchConfig(String name = 'launch-config') {
        createLaunchConfig(GradleRunConfigurationDelegate.ID, name)
    }

    protected void waitFor(int timeout = 5000, Closure condition) {
        long start = System.currentTimeMillis()
        while (!condition.call()) {
            long elapsed = System.currentTimeMillis() - start
            if (elapsed > timeout) {
                throw new RuntimeException('timeout')
            }
            Thread.sleep(50)
        }
    }

    protected int getNumOfGradleErrorMarkers() {
        gradleErrorMarkers.size()
    }

    private void deleteAllGradleErrorMarkers() {
        gradleErrorMarkers.each { it.delete() }
    }

    protected List<IMarker> getGradleErrorMarkers(IResource rootResource = workspace.root) {
        rootResource.findMarkers(GradleErrorMarker.ID, false, IResource.DEPTH_INFINITE) as List
    }

    protected List<IStatus> getPlatformLogErrors() {
        logCollector.all
    }

    private class LogCollector implements ILogListener {

        private final List<IStatus> entries = new ArrayList()

        @Override
        public synchronized void logging(IStatus status, String plugin) {
            entries.add(status)
        }

        synchronized void clear() {
            entries.clear()
        }

        synchronized List<IStatus> getAll() {
            ImmutableList.copyOf(entries)
        }
    }

    protected static List<GradleDistribution> getSupportedGradleDistributions(String versionPattern = '>=1.2') {
        GradleVersionParameterization.Default.INSTANCE.getGradleDistributions(versionPattern)
    }

    protected static String getJcenterRepositoryBlock() {
        String jcenterMirror = System.getProperty("org.eclipse.buildship.eclipsetest.mirrors.jcenter")
        if (jcenterMirror == null) {
            """
                repositories {
                    if (org.gradle.api.JavaVersion.current().isJava8Compatible()) {
                        jcenter()
                    } else {
                        maven {
                            url = "http://jcenter.bintray.com"
                        }
                    }
                }
            """
        } else {
            """
                repositories {
                    maven {
                        name = 'jcenter-mirror'
                        url "$jcenterMirror"
                    }
                }
            """
        }
    }
}
