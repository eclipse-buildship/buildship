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

import org.gradle.api.logging.Logger
import groovy.ant.AntBuilder

/**
 * Converts an Eclipse update site into a Maven repository.
 */
final class BundleMavenDeployer {
  private final AntBuilder ant
  private final String groupIdForBundles
  private final Logger logger

  private Map artifacts = [:]
  private Map artifactsNl = [:]
  private Map artifactFiles = [:]
  private Map sourceFiles = [:]

  /**
   * Creates a new instance.
   *
   * @param ant ant build instance to execute the deployment task defined in the maven-ant-tasks dependency
   * @param groupIdForBundles the maven groupId where the eclipse plugins will be published
   */
  BundleMavenDeployer(AntBuilder ant, String groupIdForBundles, Logger logger = null) {
    this.ant = ant
    this.groupIdForBundles = groupIdForBundles
    this.logger = logger
  }

  /**
   * Publishes Eclipse bundles from an update site to a Maven repository.
   *
   * @param source the update site location where the eclipse bundles are located
   * @param target the target maven repository location where the bundles will be deployed
   */
  void deploy(File source, File target) {
    File pluginFolder = new File(source, 'plugins')
    assert pluginFolder.exists()

    collectArtifacts(pluginFolder)
    fixDependencies()
    deployBundles(new DeployMavenExecutor(ant, target))
  }

  private void collectArtifacts(artifactsSourceDir) {
    def processFile = { File file ->
      logger.info("Collecting artifacts: ${file.name}")
      try {
        Bundle2Pom reader = new Bundle2Pom(group: groupIdForBundles, dependencyGroup: groupIdForBundles)
        Pom pom = reader.convert(file)
        def source_match = pom.artifact =~ /(.*)\.source/
        if (source_match) {
          def artifact = source_match[0][1]
          sourceFiles["${artifact}:${pom.version}"] = file
        } else {
          def nl_match = pom.artifact =~ /(.*)\.nl_(.*)/
          if (nl_match) {
            def artifact = nl_match[0][1]
            def language = nl_match[0][2]
            if (!artifactsNl[language])
              artifactsNl[language] = [:]
            artifactsNl[language][artifact] = pom
          } else {
            if (!artifacts.containsKey(pom.artifact))
              artifacts[pom.artifact] = []
            artifacts[pom.artifact].add pom
          }
          artifactFiles["${pom.artifact}:${pom.version}"] = file
        }
      } catch (Exception e) {
        logger.info("Error while mavenizing ${file}")
        e.printStackTrace()
      }
    }
    logger.info("Reading bundles in $artifactsSourceDir")
    try {
      artifactsSourceDir.eachDir processFile
      artifactsSourceDir.eachFileMatch ~/.*\.jar/, processFile
    } finally {
      logger.info("Finished reading bundles in $artifactsSourceDir")
    }
  }

  private void fixDependencies() {
    logger.info('Fixing dependencies')
    try {
      artifacts.each { name, artifactVersions ->
        logger.info("Fixing dependencies: $name")
        artifactVersions.each { pom ->
          pom.dependencyBundles.removeAll { reqBundle ->
            if (!artifacts[reqBundle.name.trim()]) {
              logger.info("Warning: artifact dependency $pom.group:$pom.artifact:$pom.version -> $reqBundle.name could not be resolved.")
              return true
            }
            return false
          }
          pom.dependencyBundles.each { reqBundle ->
            def resolvedVersions = artifacts[reqBundle.name.trim()]
            if (resolvedVersions.size() == 1)
              reqBundle.version = resolvedVersions[0].version
            else if (!resolvedVersions.find { it -> it.version == reqBundle.version.trim() }) {
              def compare = { a, b -> new Version(a).compare(new Version(b)) }
              resolvedVersions = resolvedVersions.sort(compare)
              int i = Collections.binarySearch resolvedVersions, reqBundle.version.trim(), compare as java.util.Comparator
              if (i < 0)
                i = -i - 1
              if (i > resolvedVersions.size() - 1)
                i = resolvedVersions.size() - 1
              def c = resolvedVersions[i]
              def depsStr = resolvedVersions.collect({ p -> "$p.group:$p.artifact:$p.version" }).join(', ')
              logger.info("Warning: resolved ambiguous dependency: $pom.group:$pom.artifact:$pom.version -> $reqBundle.name:$reqBundle.version, chosen $c.group:$c.artifact:$c.version from [$depsStr].")
              reqBundle.version = c.version
            }
          }
          artifactsNl.each { language, map_nl ->
            def pom_nl = map_nl[pom.artifact]
            if (pom_nl)
              pom.dependencyBundles.each { dep_bundle ->
                def dep_pom_nl = map_nl[dep_bundle.name]
                if (dep_pom_nl) {
                  pom_nl.dependencyBundles.add new DependencyBundle(name: dep_pom_nl.artifact, version: dep_pom_nl.version, visibility: dep_bundle.visibility, resolution: dep_bundle.resolution)
                }
              }
          }
        }
      }
    } finally {
      logger.info('Finished fixing dependencies')
    }
  }

  private deployBundles(DeployMavenExecutor executor) {
    logger.info('Deploying artifacts')
    try {
      artifacts.each { name, artifactVersions ->
        artifactVersions.each { pom ->
          executor.deployBundle pom, artifactFiles["${pom.artifact}:${pom.version}"], sourceFile: sourceFiles["${pom.artifact}:${pom.version}"]
        }
      }
      artifactsNl.each { language, map_nl ->
        map_nl.each { artifactName, pom ->
          executor.deployBundle pom, artifactFiles["${pom.artifact}:${pom.version}"]
        }
      }
    } finally {
      executor.cleanup()
      logger.info('Finished deploying artifacts')
    }
  }
}

