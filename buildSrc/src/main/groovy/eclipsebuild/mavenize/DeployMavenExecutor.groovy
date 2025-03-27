/**
 * Copyright (c) 2014  Andrey Hihlovskiy
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 */

package eclipsebuild.mavenize

import org.apache.maven.artifact.Artifact
import org.apache.maven.artifact.DefaultArtifact
import org.apache.maven.artifact.deployer.ArtifactDeployer
import org.apache.maven.artifact.handler.DefaultArtifactHandler
import org.apache.maven.artifact.repository.ArtifactRepository
import org.apache.maven.artifact.repository.DefaultArtifactRepository
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout
import org.apache.maven.artifact.versioning.DefaultArtifactVersion
import org.apache.maven.artifact.versioning.VersionRange
import org.codehaus.classworlds.ClassWorld
import org.codehaus.classworlds.DuplicateRealmException
import org.codehaus.plexus.PlexusContainer
import org.codehaus.plexus.PlexusContainerException
import org.codehaus.plexus.embed.Embedder
import groovy.ant.AntBuilder

/**
 * Deploys OSGI bundle (jar or directory) to maven repository
 */
class DeployMavenExecutor {

    private final AntBuilder ant
    private final File target
    private final File workFolder

    /**
     * Constructs Deployer with the specified parameters.
     */
    DeployMavenExecutor(AntBuilder ant, File target) {
        this.ant = ant
        this.target = target
        this.workFolder = new File(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString())
    }

    /**
     * Deploys the specified bundle with the specified POM to target maven repository.
     * @param options - may contain sourceFile (of type java.io.File), pointing to sources jar.
     * @param pomStruct - contains POM that will be used for deployment
     * @param bundleFileOrDirectory - jar-file or directory, containing OSGI bundle
     */
    void deployBundle(Map options = [:], Pom pomStruct, File bundleFileOrDirectory) {
        workFolder.mkdirs()
        String temporaryM2FolderPath = new File(workFolder, ".m2").absolutePath
        def pomFile = new File(workFolder, 'myPom.xml')
        File bundleFile
        if (bundleFileOrDirectory.isDirectory()) {
            pomStruct.packaging = 'jar'
            pomFile.text = pomStruct.toString()
            File zipFile = new File(workFolder, "${pomStruct.artifact}-${pomStruct.version}.jar")
            ant.zip(basedir: bundleFileOrDirectory, destfile: zipFile)
            bundleFile = zipFile
        }
        else {
            pomFile.text = pomStruct.toString()
            bundleFile = bundleFileOrDirectory
        }
        File sourceFile = options.sourceFile
        if(sourceFile?.isDirectory()) {
            File zipFile = new File(workFolder, sourceFile.name + '.jar')
            ant.zip(basedir: sourceFile, destfile: zipFile)
            sourceFile = zipFile
        }

        // create deployer
        ArtifactRepository localRepository = new DefaultArtifactRepository('local.repository', 'file://' + temporaryM2FolderPath, new DefaultRepositoryLayout())
        ArtifactRepository remoteRepository = new DefaultArtifactRepository('remote.repository', 'file://' + this.target.toURI().toURL().toString(), new DefaultRepositoryLayout())
        ArtifactDeployer deployer = createContainer().lookup(ArtifactDeployer.ROLE)

        // deploy pom
        Artifact artifactPom = new DefaultArtifact(pomStruct.group, pomStruct.artifact, new VersionRange(new DefaultArtifactVersion(pomStruct.version), []), 'pom', 'pom', "" /*classifier*/, new DefaultArtifactHandler('pom'))
        artifactPom.setFile(pomFile)
        deployer.deploy(pomFile, artifactPom, remoteRepository, localRepository)

        // deploy jar
        Artifact artifactJar = new DefaultArtifact(pomStruct.group, pomStruct.artifact, new VersionRange(new DefaultArtifactVersion(pomStruct.version), []), 'compile', 'jar', "" /*classifier*/, new DefaultArtifactHandler('jar'))
        artifactJar.setFile(bundleFile)
        deployer.deploy(bundleFile, artifactJar, remoteRepository, localRepository)

        // deploy sources
        if(sourceFile) {
            Artifact artifactSourcesJar = new DefaultArtifact(pomStruct.group, pomStruct.artifact, new VersionRange(new DefaultArtifactVersion(pomStruct.version), []), 'compile', 'jar', "sources" /*classifier*/, new DefaultArtifactHandler('jar'))
            artifactSourcesJar.setFile(sourceFile)
            deployer.deploy(sourceFile, artifactSourcesJar, remoteRepository, localRepository)
        }
    }

    private PlexusContainer createContainer() {
        try {
            ClassWorld classWorld = new ClassWorld()
            classWorld.newRealm("plexus.core", getClass().getClassLoader())
            Embedder embedder = new Embedder()
            embedder.start(classWorld)
            return embedder.getContainer()
        }
        catch (PlexusContainerException e) {
            throw new RuntimeException("Unable to start embedder", e);
        }
        catch (DuplicateRealmException e) {
            throw new RuntimeException("Unable to create embedder ClassRealm", e);
        }
    }

    void cleanup() {
        // delete the working directory
        if(workFolder.exists()){
            workFolder.deleteDir()
        }
        // clean up md5 sum files generated by the ant maven deploy task
        new File(System.getProperty('java.io.tmpdir')).eachFileMatch(~/maven-artifact\d+\.tmp/) { it.delete() }
    }
}

