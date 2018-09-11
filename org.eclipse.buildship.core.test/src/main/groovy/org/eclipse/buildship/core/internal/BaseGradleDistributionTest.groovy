/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.eclipse.buildship.core.internal

import org.gradle.tooling.GradleConnector
import spock.lang.Specification

import org.eclipse.buildship.core.GradleDistributions

class BaseGradleDistributionTest extends Specification {

    def "GradleDistrubution configures GradleConnector to use local installation"() {
        setup:
        GradleConnector connector = Mock(GradleConnector.class)
        File file = new File('.')
        BaseGradleDistribution distribution = GradleDistributions.forLocalInstallation(file)

        when:
        distribution.apply(connector)

        then:
        1 * connector.useInstallation(_)
    }

    def "GradleDistrubution configures GradleConnector to use remote distribution"() {
        setup:
        GradleConnector connector = Mock(GradleConnector.class)
        URI uri = new File('.').toURI()
        BaseGradleDistribution distribution = GradleDistributions.forRemoteDistribution(uri)

        when:
        distribution.apply(connector)

        then:
        1 * connector.useDistribution(uri)
    }

    def "GradleDistrubution configures GradleConnector to use version number"() {
        setup:
        GradleConnector connector = Mock(GradleConnector.class)
        String version = '2.0'
        BaseGradleDistribution distribution = GradleDistributions.forVersion(version)

        when:
        distribution.apply(connector)

        then:
        1 * connector.useGradleVersion(version)
    }

    def "GradleDistrubution configures GradleConnector to use default distibution defined by the Tooling API library"() {
        setup:
        GradleConnector connector = Mock(GradleConnector.class)
        BaseGradleDistribution distribution = GradleDistributions.fromBuild()

        when:
        distribution.apply(connector)

        then:
        1 * connector.useBuildDistribution()
    }

    def "GradleDistrubution has human-readable toString() implementation"() {
        when:
        File file = new File('.')
        BaseGradleDistribution distribution = GradleDistributions.forLocalInstallation(file)

        then:
        distribution.toString() == "Local installation at " + file.absolutePath

        when:
        distribution = GradleDistributions.forRemoteDistribution(file.toURI())

        then:
        distribution.toString() == "Remote distribution from " + file.toURI().toString()

        when:
        distribution = GradleDistributions.forVersion('2.1')

        then:
        distribution.toString() == "Specific Gradle version 2.1"

        when:
        distribution = GradleDistributions.fromBuild()

        then:
        distribution.toString() == "Gradle wrapper from target build"
    }
}
