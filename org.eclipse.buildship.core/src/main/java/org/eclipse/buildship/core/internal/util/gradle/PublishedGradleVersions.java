/*******************************************************************************
 * Copyright (c) 2023 Gradle Inc. and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.core.internal.util.gradle;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.gradle.api.UncheckedIOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharSource;
import com.google.common.io.CharStreams;
import com.google.common.io.Closeables;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import org.eclipse.buildship.core.internal.CorePlugin;

/**
 * Provides information about the Gradle versions available from services.gradle.org. The version information can optionally be cached on the local file system.
 *
 * @author Etienne Studer
 */
public final class PublishedGradleVersions {

    // end-point that provides full version information
    private static final String VERSIONS_URL = "https://services.gradle.org/versions/all";

    // the minimum Gradle version considered
    private static final String MINIMUM_SUPPORTED_GRADLE_VERSION = "2.6";

    // JSON keys
    private static final String VERSION = "version";
    private static final String SNAPSHOT = "snapshot";
    private static final String ACTIVE_RC = "activeRc";
    private static final String RC_FOR = "rcFor";
    private static final String BROKEN = "broken";

    private static final Logger LOG = LoggerFactory.getLogger(PublishedGradleVersions.class);

    private final List<Map<String, String>> versions;

    private PublishedGradleVersions(List<Map<String, String>> versions) {
        this.versions = ImmutableList.copyOf(versions);
    }

    /**
     * Returns all final Gradle versions plus the latest active release candidate, if available.
     *
     * @return the matching versions
     */
    public List<GradleVersion> getVersions() {
        return FluentIterable.from(this.versions).filter(new Predicate<Map<String, String>>() {
            @Override
            public boolean apply(Map<String, String> input) {
                return (Boolean.valueOf(input.get(ACTIVE_RC)) || input.get(RC_FOR).equals("")) &&
                        !Boolean.valueOf(input.get(BROKEN)) &&
                        !Boolean.valueOf(input.get(SNAPSHOT));
            }
        }).transform(new Function<Map<String, String>, GradleVersion>() {
            @Override
            public GradleVersion apply(Map<String, String> input) {
                return GradleVersion.version(input.get(VERSION));
            }
        }).filter(new Predicate<GradleVersion>() {
            @Override
            public boolean apply(GradleVersion input) {
                return input.compareTo(GradleVersion.version(MINIMUM_SUPPORTED_GRADLE_VERSION)) >= 0;
            }
        }).toList();
    }

    /**
     * Creates a new instance based on the version information available on services.gradle.org.
     *
     * @param lookupStratgy the strategy to use when retrieving the versions
     * @return the new instance
     */
    public static PublishedGradleVersions create(LookupStrategy lookupStratgy) {
        if (lookupStratgy == LookupStrategy.REMOTE) {
            LOG.info("Gradle version information caching disabled. Remote download required.");
            String json = downloadVersionInformation();
            return create(json);
        }
        File cacheFile = getCacheFile();
        if (!cacheFile.isFile() || !cacheFile.exists()) {
            LOG.info("Gradle version information cache is not available. Remote download required.");
            return tryToDownloadAndCacheVersions(cacheFile, lookupStratgy);
        }

        if (cacheFile.lastModified() > System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)) {
            LOG.info("Gradle version information cache is up-to-date. Trying to read.");
            return tryToReadUpToDateVersionsFile(cacheFile, lookupStratgy);
        } else {
            LOG.info("Gradle version information cache is out-of-date. Trying to update.");
            return tryToUpdateOutdatedVersionsFile(cacheFile, lookupStratgy);
        }
    }

    private static PublishedGradleVersions tryToReadUpToDateVersionsFile(File cacheFile, LookupStrategy lookupStratgy) {
        Optional<String> cachedVersions = readCacheVersionsFile(cacheFile);
        if (cachedVersions.isPresent()) {
            return create(cachedVersions.get());
        } else {
            LOG.error("Cannot read Gradle version information cache. Remote download required.");
            return tryToDownloadAndCacheVersions(cacheFile, lookupStratgy);
        }
    }

    private static PublishedGradleVersions tryToUpdateOutdatedVersionsFile(File cacheFile, LookupStrategy lookupStratgy) {
        try {
            return tryToDownloadAndCacheVersions(cacheFile, lookupStratgy);
        } catch (RuntimeException e) {
            Optional<String> cachedVersions = readCacheVersionsFile(cacheFile);
            if (cachedVersions.isPresent()) {
                LOG.info("Updating Gradle version information cache failed. Using outdated cache.");
                return create(cachedVersions.get());
            } else {
                throw new IllegalStateException("Cannot collect Gradle version information remotely nor locally.", e);
            }
        }
    }

    private static PublishedGradleVersions tryToDownloadAndCacheVersions(File cacheFile, LookupStrategy lookupStratgy) {
        if (lookupStratgy == LookupStrategy.CACHED_ONLY) {
            throw new IllegalStateException("Could not get Gradle version information from cache and remote update was disabled");
        }
        String json = downloadVersionInformation();
        storeCacheVersionsFile(json, cacheFile);
        return create(json);
    }

    private static String downloadVersionInformation() {
        HttpURLConnection connection = null;
        InputStreamReader reader = null;
        try {
            URL url = createURL(VERSIONS_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            reader = new InputStreamReader(connection.getInputStream(), Charsets.UTF_8);
            return CharStreams.toString(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot download published Gradle versions.", e);
            // throw an exception if version information cannot be downloaded since we need this information
        } finally {
            try {
                Closeables.close(reader, false);
            } catch (IOException e) {
                LOG.warn("Can't close stream after downloading published Gradle versions", e);
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static void storeCacheVersionsFile(String json, File cacheFile) {
        //noinspection ResultOfMethodCallIgnored
        cacheFile.getParentFile().mkdirs();

        try {
            CharSource.wrap(json).copyTo(Files.asCharSink(cacheFile, Charsets.UTF_8));
        } catch (IOException e) {
            LOG.error("Cannot write Gradle version information cache file.", e);
            // do not throw an exception if cache file cannot be written to be more robust against file system problems
        }
    }

    private static Optional<String> readCacheVersionsFile(File cacheFile) {
        try {
            return Optional.of(Files.toString(cacheFile, Charsets.UTF_8));
        } catch (IOException e) {
            LOG.error("Cannot read found Gradle version information cache file.", e);
            // do not throw an exception if cache file cannot be read to be more robust against file system problems
            return Optional.absent();
        }
    }

    private static PublishedGradleVersions create(String json) {
        // convert versions from JSON String to JSON Map
        Gson gson = new GsonBuilder().create();
        TypeToken<List<Map<String, String>>> typeToken = new TypeToken<List<Map<String, String>>>() {
        };
        List<Map<String, String>> versions = gson.fromJson(json, typeToken.getType());

        // create instance
        return new PublishedGradleVersions(versions);
    }

    private static URL createURL(String url) {
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid URL: " + url, e);
        }
    }

    private static File getCacheFile() {
        // ensures compliance with XDG base spec: https://specifications.freedesktop.org/basedir-spec/basedir-spec-latest.html
        String xdgCache = System.getenv("XDG_CACHE_HOME");
        if (xdgCache != null) {
            return new File(xdgCache, "tooling/gradle/versions.json");
        }
        return CorePlugin.getInstance().getStateLocation().append("gradle").append("versions.json").toFile();
    }

    /**
     * Determines how Gradle versions are retrieved.
     *
     * @author Stefan Oehme
     */
    public static enum LookupStrategy {
        /**
         * Look only in the local cache file. Fail if it does not exist or is unreadable.
         */
        CACHED_ONLY,
        /**
         * Look in the local cache file first. Try a remote call if it cannot be read.
         * If the remote call succeeds, store the result in the cache.
         * Fail if the remote call fails.
         */
        REMOTE_IF_NOT_CACHED,
        /**
         * Disable caching, execute a remote call directly.
         * Fail if the remote call fails.
         */
        REMOTE
    }

}
