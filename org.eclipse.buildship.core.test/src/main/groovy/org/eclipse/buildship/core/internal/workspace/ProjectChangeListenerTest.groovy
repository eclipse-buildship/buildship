/*******************************************************************************
 * Copyright (c) 2023 Gradle Inc. and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.core.internal.workspace

import spock.lang.Ignore

import org.eclipse.core.runtime.NullProgressMonitor

import org.eclipse.buildship.core.internal.CorePlugin
import org.eclipse.buildship.core.internal.event.EventListener
import org.eclipse.buildship.core.internal.test.fixtures.WorkspaceSpecification

class ProjectChangeListenerTest extends WorkspaceSpecification {

    EventListener listener

    def setup() {
        newProject('existing-project')
        listener = Mock(EventListener)
        CorePlugin.listenerRegistry().addEventListener(listener)
    }

    def cleanup() {
        CorePlugin.listenerRegistry().removeEventListener(listener)
    }

    @Ignore
    def "Can listen to project creation events"() {
        when:
        newProject('project')

        then:
        1 * listener.onEvent({ it instanceof ProjectCreatedEvent})
    }

    def "Can listen to project deletion events"() {
        when:
        deleteAllProjects(true)

        then:

        1 * listener.onEvent({ it instanceof ProjectDeletedEvent && it.project.name == 'existing-project' })
    }

    def "Can listen to project rename events"() {
        when:
        CorePlugin.workspaceOperations().renameProject(findProject('existing-project'), 'moved-project', new NullProgressMonitor())

        then:
        1 * listener.onEvent({ it instanceof ProjectMovedEvent && it.project.name == 'moved-project' && it.previousName == 'existing-project' })
    }
}
