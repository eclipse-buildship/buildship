/*******************************************************************************
 * Copyright (c) 2023 Gradle Inc. and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.core.internal;

import org.eclipse.buildship.core.GradleBuild;
import org.eclipse.buildship.core.internal.configuration.BuildConfiguration;
import org.eclipse.buildship.core.internal.operation.ToolingApiStatus;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.internal.launching.StandardVMType;
import org.gradle.api.JavaVersion;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.model.build.BuildEnvironment;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class CompatibilityChecker {
    public static final String BYPASS_COMPATIBILITY_CHECK_KEY = "org.eclipse.buildship.integtest.bypassToolingApiCompatibilityChecks";
    // <JDK, unsupported Gradle versions>
    public static final Map<String, Set<String>> compatibilityMap = Collections.unmodifiableMap(loadCompatibilityMap());

    private static final String PROPERTIES_FILE = "/org/eclipse/buildship/core/internal/gradle/java-unsupported-gradle.properties";
    private static final String UNSUPPORTED_BUILD_ENVIRONMENT_MESSAGE = "Could not create an instance of Tooling API implementation using the specified Gradle distribution";

    public static IStatus validateToolingApiCompatibility(GradleBuild gradleBuild, BuildConfiguration buildConfig, IProgressMonitor monitor) {
        String bypassCompatibilityCheckProperty = System.getProperty(BYPASS_COMPATIBILITY_CHECK_KEY);
        if (!"true".equals(bypassCompatibilityCheckProperty)) {
            File javaHome = buildConfig.getJavaHome();
            String javaVersion = javaHome != null ? new StandardVMType().readReleaseVersion(javaHome) : System.getProperty("java.version");
            if (javaVersion == null || javaVersion.isEmpty()) {
                // Skip the compatibility check if java version can't be detected
                return Status.OK_STATUS;
            }
            String gradleVersion;
            try {
                BuildEnvironment environment = gradleBuild.withConnection(connection -> connection.getModel(BuildEnvironment.class), monitor);
                gradleVersion = environment.getGradle().getGradleVersion();
            } catch (Exception e) {
                if (hasUnsupportedBuildEnvironmentMessage(e)) {
                    return ToolingApiStatus.from("Project synchronization", new UnsupportedJavaVersionException(String.format("The current build uses Java %s which is not supported. Please consult the Gradle documentation to find the compatible combinations: https://docs.gradle.org/current/userguide/compatibility.html.", javaVersion)));
                }
                return ToolingApiStatus.from("Project synchronization", e);
            }
            if (gradleVersion == null) {
                return ToolingApiStatus.from("Project synchronization", new GradleConnectionException("Can't determine Gradle version when synchronizing project."));
            }
            JavaVersion javaVersionObject = JavaVersion.toVersion(javaVersion);
            Set<String> unsupportedGradleVersions = compatibilityMap.get(javaVersionObject.getMajorVersion());
            if (unsupportedGradleVersions != null && unsupportedGradleVersions.contains(gradleVersion)) {
                return ToolingApiStatus.from("Project synchronization", new UnsupportedJavaVersionException(String.format("The current build uses Gradle %s running on Java %s which is not supported. Please consult the Gradle documentation to find the compatible combinations: https://docs.gradle.org/current/userguide/compatibility.html.", gradleVersion, javaVersion)));
            }
        }
        return new Status(IStatus.OK, CorePlugin.PLUGIN_ID, "tooling API compatibility check passed");
    }

    private static boolean hasUnsupportedBuildEnvironmentMessage(Exception e) {
        Set<Throwable> seen = new HashSet<>();
        Throwable current = e;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains(UNSUPPORTED_BUILD_ENVIRONMENT_MESSAGE)) {
                return true;
            }
            if (!seen.add(current)) {
                break;
            }
            current = current.getCause();
        }
        return false;
    }

    private static Map<String, Set<String>> loadCompatibilityMap() throws GradlePluginsRuntimeException {
        Map<String, Set<String>> compatibilityMatrix = new HashMap<>();
        URL resource = CompatibilityChecker.class.getResource(PROPERTIES_FILE);
        if (resource == null) {
            throw new GradlePluginsRuntimeException(String.format("Resource '%s' not found.", PROPERTIES_FILE));
        }
        InputStream inputStream = null;
        try {
            URLConnection connection = resource.openConnection();
            connection.setUseCaches(false);
            inputStream = connection.getInputStream();
            Properties properties = new Properties();
            properties.load(inputStream);
            properties.entrySet().forEach(e -> {
                Object javaVersion = e.getKey();
                Object gradleVersions = e.getValue();
                if (javaVersion instanceof String && gradleVersions instanceof String) {
                    compatibilityMatrix.put((String) javaVersion, new HashSet<>(Arrays.asList(((String) gradleVersions).split(","))));
                }
            });
        } catch (Exception e) {
            throw new GradlePluginsRuntimeException(String.format("Could not load version details from resource '%s'.", resource), e);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    throw new GradlePluginsRuntimeException(e);
                }
            }
        }
        return compatibilityMatrix;
    }
}
