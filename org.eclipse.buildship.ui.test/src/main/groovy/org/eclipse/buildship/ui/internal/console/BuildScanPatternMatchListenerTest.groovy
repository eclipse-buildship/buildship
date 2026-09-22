/*******************************************************************************
 * Copyright (c) 2026 Gradle Inc. and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.ui.internal.console

import java.util.regex.Matcher
import java.util.regex.Pattern

import spock.lang.Specification

class BuildScanPatternMatchListenerTest extends Specification {

    private static final String SCAN_URL = 'https://develocity.example.com/s/3z475bz247h5g'

    def "Recognizes the Build Scan URL announced by any plugin generation"() {
        expect:
        scanUrlIn(publication(announcement)) == SCAN_URL

        where:
        announcement << [
            'Publishing build information...',         // build-scan-plugin 1.x
            'Publishing build scan...',                // Gradle Enterprise and Develocity 3.x
            'Publishing Build Scan...',                // Develocity 4.1
            'Publishing Build Scan to Develocity...'   // Develocity 4.4
        ]
    }

    def "Recognizes the Build Scan URL in output using CRLF line endings"() {
        expect:
        scanUrlIn(publication('Publishing Build Scan to Develocity...', '\r\n')) == SCAN_URL
    }

    def "Ignores console output that announces no Build Scan URL"() {
        expect:
        scanUrlIn(consoleOutput) == null

        where:
        consoleOutput << [
            'Publishing a Build Scan to Develocity at gradle.com requires accepting the Gradle Terms of Use.\nhttps://gradle.com/help/legal-terms-of-use\n',
            '\nPublishing Build Scan to Develocity...\nPublishing failed.\n',
            '> Task :foo\nfoo\n\nBUILD SUCCESSFUL in 1s\n'
        ]
    }

    private static String scanUrlIn(String consoleOutput) {
        BuildScanPatternMatchListener listener = new BuildScanPatternMatchListener()
        Matcher matcher = Pattern.compile(listener.pattern, listener.compilerFlags).matcher(consoleOutput)
        matcher.find() ? matcher.group().substring(matcher.group().indexOf('http')) : null
    }

    private static String publication(String announcement, String newLine = '\n') {
        "BUILD SUCCESSFUL in 3s${newLine}${newLine}${announcement}${newLine}${SCAN_URL}${newLine}"
    }
}
