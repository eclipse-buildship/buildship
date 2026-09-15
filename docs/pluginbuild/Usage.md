# Gradle-based Eclipse project build

## Building

Buildship is built using a set of plugins that support dependency resolution, processing Eclipse descriptors, and
publishing Eclipse update sites. The scripts for the plugins are located in the _buildSrc_ folder.

The plugin scripts in _buildSrc_ expect a small set of Eclipse bundles/libraries in the
_buildSrc/libs_ folder that are committed to the Git repository to avoid bootstrapping problems. All
plugins are defined in the `eclipsebuild` package.

The plugin version is specified in the _version.txt_ file.


### BuildDefinitionPlugin

The `BuildDefinitionPlugin` Gradle plugin has to be applied on the root project. It defines the target platform for
the build and makes all Eclipse plugins available for dependency resolution. The `BundlePlugin`, `FeaturePlugin`, and
`UpdateSitePlugin` Gradle plugins all depend on the tasks provided by the `BuildDefinitionPlugin`.

The set of supported target platforms are declared in the root _build.gradle_ file. For example:

    apply plugin: eclipsebuild.BuildDefinitionPlugin

    eclipseBuild {
        defaultEclipseVersion = '44'

        targetPlatform {
            eclipseVersion = '44'
            targetDefinition = file('tooling-e44.target')
            versionMapping = [
                'org.eclipse.core.runtime' : '3.10.0.v20140318-2214'
            ]
        }

        targetPlatform {
             eclipseVersion = '43'
             ...
         }
     }

When running a Gradle build using the above configuration, the build uses the Eclipse target platform version `44`. The
build can use an alternative target platform by specifying the `eclipse.version` Gradle project property. For example:

    gradle build -Peclipse.version=43

The contributed task `installTargetPlatform` creates the target platform. It does the following:

1. Downloads an Eclipse SDK in the specified version
2. Installs the specified features defined in the target definition file
3. Converts all plugins from the downloaded Eclipse SDK into a Maven repository

Once the 'mavenized' target platform is available, the Gradle build configuration can reference Eclipse plugin dependencies
like any other dependency. The _groupId_ is always `eclipse`. For example:

    api 'eclipse:org.eclipse.jdt.ui:+'

The generated target platform is stored under the root project's build directory, in
_build/tooling/&lt;eclipse-version&gt;_. Each checkout therefore keeps its own copy, and `gradle clean` removes it.


The `versionMapping` can be used to define exact plugin dependency versions per target platform. A bundle can define a dependency
through the {@code withEclipseBundle()} method like

    api withEclipseBundle('org.eclipse.core.runtime')

If the active target platform has a version mapped for the dependency then that version is used, otherwise an unbound version range (+) is applied.


### BundlePlugin

The `BundlePlugin` Gradle plugin knows how to build Eclipse plugins and needs to be applied on the plugin project(s). The plugin creates
a jar file containing all elements defined in the feature project's _build.properties_. The bundle manifest file _META-INF/MANIFEST.MF_
is copied into the generated jar file and the version is replaced with the one from the current build.

The `BundlePlugin` also adds the capability to bundle jars along with the dependencies from the target platform. This can be
achieved by declaring `bundled` dependencies. For example:

    apply plugin: eclipsebuild.BundlePlugin

    dependencies {
        api 'eclipse:org.eclipse.core.runtime:+'
        bundled 'com.google.guava:guava:18.0'
    }

The dependencies of the `bundled` configuration are used by the `updateLibs` task. This task downloads the transitive
dependencies into the _lib_ folder, updates the manifest file to reference these dependencies and updates the _.classpath_ file.


### TestBundlePlugin

The `TestBundlePlugin` Gradle plugin is an extension of the `BundlePlugin` and knows how to run tests. For example:

    apply plugin: eclipsebuild.TestBundlePlugin

    eclipseTest {
        fragmentHost 'org.eclipse.buildship.core'
        applicationName 'org.eclipse.pde.junit.runtime.coretestapplication'
        optionsFile file('.options')
    }

The plugin adds a task called `eclipseTest`. This task does the following:

1. Clears the _buildDir/eclipseTest_ folder in order to have a clean environment for testing.
2. Copies the non-mavenized target platform to the _buildDir/eclipseTest_ folder.
3. Installs the project dependency plugins into the target platform with the _P2 director_.
4. Executes the target platform and runs the tests with a custom Gradle test runner (which is an `ITestRunListener` implementation).

Currently all concrete classes are gathered by the test plugin for test execution. The test results are collected in the _build/reports_ folder.

The implementation uses techniques defined in the article [PDE Unit Tests using Ant](http://www.eclipse.org/articles/article.php?file=Article-PDEJUnitAntAutomation/index.html).


### FeaturePlugin

The `FeaturePlugin` Gradle plugin knows how to build an Eclipse feature and needs to be applied on the feature project(s). The plugin creates
a jar file containing all elements defined in the feature project's _build.properties_.


### UpdateSitePlugin

The `UpdateSitePlugin` Gradle plugin knows how to build an update site and needs to be applied on the site project. The plugins adds
a task called `createP2Repository`. This task takes all project dependencies and, if they are either Eclipse plugins or Eclipse features, publishes
them to a local P2 repository. The generated repository is available in the _build/repository_ folder. For example:

    apply plugin: eclipsebuild.UpdateSitePlugin

    updateSite {
        siteDescriptor = file('category.xml')
        extraResources = files('epl-v10.html', 'readme.txt')
        p2ExtraProperties = ['p2.mirrorsURL' : 'http://www.eclipse.org/downloads/download.php?file=/path/to/repository&format=xml' ]
        signBundles = true
    }

The `siteDescriptor` should point to a category descriptor which is used to publish update sites. The `extraResources` allows to reference
extra resources to add to the update site. The `p2ExtraProperties` allows to add _properties_ elements to the update site's _artifacts.xml_
file under the _repository/properties_ node. The `signBundles` specifies whether the artifacts of the update site should be signed (default is false).

### Adding a new Eclipse plugin

* Create a new folder under the _buildship_ root folder
* Create a project in that folder through Eclipse and use the same name for the project as for the folder
* Create a _build.gradle_ file and apply the `BundlePlugin`
* Add the project to the _settings.gradle_ file
