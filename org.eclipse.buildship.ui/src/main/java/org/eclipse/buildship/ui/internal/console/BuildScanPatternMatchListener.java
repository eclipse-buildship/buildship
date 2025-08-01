/*******************************************************************************
 * Copyright (c) 2023 Gradle Inc. and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.ui.internal.console;

import java.util.regex.Pattern;

import com.google.common.base.Optional;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.ui.console.IPatternMatchListener;
import org.eclipse.ui.console.PatternMatchEvent;
import org.eclipse.ui.console.TextConsole;

import org.eclipse.buildship.core.internal.CorePlugin;
import org.eclipse.buildship.core.internal.console.ProcessDescription;
import org.eclipse.buildship.core.internal.scan.BuildScanCreatedEvent;
import org.eclipse.buildship.core.internal.util.string.PatternUtils;

/**
 * Finds build scan URL console output.
 *
 * @author Donat Csikos
 */
public final class BuildScanPatternMatchListener implements IPatternMatchListener {

    private GradleConsole console;

    @Override
    public void connect(TextConsole console) {
        this.console = (GradleConsole) console;
    }

    @Override
    public void disconnect() {
        this.console = null;
    }

    @Override
    public void matchFound(PatternMatchEvent event) {
        try {
            int offset = event.getOffset();
            int length = event.getLength();
            String buildScansInfo = this.console.getDocument().get(offset, length);
            String buildScanUrl = buildScansInfo.substring(buildScansInfo.indexOf("http"));
            Optional<ProcessDescription> description = this.console.getProcessDescription();
            if (description.isPresent()) {
                CorePlugin.listenerRegistry().dispatch(new BuildScanCreatedEvent(buildScanUrl, description.get()));
            }
        } catch (BadLocationException e) {
        }
    }

    @Override
    public String getPattern() {
        return "Publishing (?:build information|build scan|Build Scan).*\\s+" + PatternUtils.WEB_URL_PATTERN;
    }

    @Override
    public int getCompilerFlags() {
        return Pattern.MULTILINE;
    }

    @Override
    public String getLineQualifier() {
        return null;
    }
}
