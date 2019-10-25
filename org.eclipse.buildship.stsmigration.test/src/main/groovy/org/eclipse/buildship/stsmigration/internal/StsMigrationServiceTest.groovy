/*******************************************************************************
 * Copyright (c) 2019 Gradle Inc.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.stsmigration.internal

import spock.lang.Specification

import org.eclipse.buildship.stsmigration.internal.StsMigrationDialog
import org.eclipse.buildship.stsmigration.internal.StsMigrationService
import org.eclipse.buildship.stsmigration.internal.StsMigrationState

class StsMigrationServiceTest extends Specification {

    def "If the STS plugin is not installed then dialog is not presented"() {
        setup:
        StsMigrationState migrationState = createMigrationState(false, false)
        StsMigrationDialog.Factory dialogFactory = createDialogFactory()
        StsMigrationService service = new StsMigrationService(migrationState, dialogFactory)

        when:
        service.run()

        then:
        0 * dialogFactory.newInstance(null,null).open()
    }

    def "If the STS plugin is installed then dialog is presented"() {
        setup:
        StsMigrationState migrationState = createMigrationState(true, false)
        StsMigrationDialog.Factory dialogFactory = createDialogFactory()
        StsMigrationService service = new StsMigrationService(migrationState, dialogFactory)

        when:
        service.run()

        then:
        1 * dialogFactory.newInstance(null,null).open()
    }

    def "If the notification is muted then the dialog is not presented regardless that the STS plugin is installed"() {
        setup:
        StsMigrationState migrationState = createMigrationState(stsPluginInstalled, true)
        StsMigrationDialog.Factory dialogFactory = createDialogFactory()
        StsMigrationService service = new StsMigrationService(migrationState, dialogFactory)

        when:
        service.run()

        then:
        0 * dialogFactory.newInstance(null,null).open()

        where:
        stsPluginInstalled << [false, true]
    }

    private def createMigrationState(boolean stsInstalled, boolean notifMuted) {
        StsMigrationState migrationState = Mock(StsMigrationState)
        migrationState.stsPluginInstalled >> stsInstalled
        migrationState.notificationMuted >> notifMuted
        migrationState
    }

    private def createDialogFactory() {
        StsMigrationDialog dialog = Mock(StsMigrationDialog)
        StsMigrationDialog.Factory factory = Mock(StsMigrationDialog.Factory)
        factory.newInstance(_,_) >> dialog
        factory
    }

}
