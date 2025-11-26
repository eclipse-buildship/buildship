/*******************************************************************************
 * Copyright (c) 2025 Gradle Inc. and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.core.internal

import org.eclipse.buildship.core.internal.test.fixtures.ProjectSynchronizationSpecification
import org.eclipse.buildship.core.internal.ToolingApiSystemProperties
import org.eclipse.buildship.core.SynchronizationResult
import org.eclipse.core.runtime.NullProgressMonitor
import org.gradle.tooling.ProjectConnection

class ToolingApiSystemPropertiesTest extends ProjectSynchronizationSpecification {


    def "any tooling api interaction provides buildship dot active system property"() {
		given:
        File location = dir('under-test-build')  {
	        file 'settings.gradle', """
	            def active = "${ToolingApiSystemProperties.ACTIVE_NAME}"
	        	if (System.getProperty("\$active") != "true") {
                    throw new Exception("UNSET PROPERTY: \$active")
                }
        	"""
        }


       	expect:
       	def gradleBuild = gradleBuildFor(location)
        def query = { ProjectConnection c -> c.newBuild().forTasks("help").run(); true }
       	gradleBuild.withConnection(query, new NullProgressMonitor());
	}

	def "ide synchronization provides buildship dot sync dot active system property"() {
		given:
        File location = dir('under-test-build')  {
	        file 'settings.gradle', """
	            def active = "${ToolingApiSystemProperties.ACTIVE_NAME}"
	            def activeSync = "${ToolingApiSystemProperties.SYNC_ACTIVE_NAME}"
                if (System.getProperty("\$active") != "true") {
                    throw new Exception("UNSET PROPERTY: \$active")
                }
	        	if (System.getProperty("\$activeSync") != "true") {
                    throw new Exception("UNSET PROPERTY: \$activeSync")
                }
        	"""
        }

       	when:
       	def gradleBuild = gradleBuildFor(location)
        SynchronizationResult result = gradleBuild.synchronize(null)

        then:
        assertResultOkStatus(result)
    }
}
