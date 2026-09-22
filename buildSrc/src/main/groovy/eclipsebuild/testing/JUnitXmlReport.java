/*
 * Copyright (c) 2026 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Donát Csikós (Gradle Inc.) - initial API and implementation and initial documentation
 */

package eclipsebuild.testing;

import org.gradle.api.file.Directory;
import org.gradle.api.internal.tasks.testing.TestReportGenerator;
import org.gradle.api.internal.tasks.testing.junit.result.JUnitXmlResultOptions;
import org.gradle.api.internal.tasks.testing.report.generic.JunitXmlTestReportGenerator;
import org.gradle.api.model.ObjectFactory;

import java.util.Collections;

final class JUnitXmlReport {

    private JUnitXmlReport() {
    }

    /**
     * Writes one {@code TEST-<class name>.xml} file per test class into {@code xmlResultsDir}.
     */
    static void generate(ObjectFactory objectFactory, Directory binaryResultsDir, Directory xmlResultsDir) {
        JUnitXmlResultOptions options = new JUnitXmlResultOptions(false, false, true, true);
        TestReportGenerator generator = objectFactory.newInstance(JunitXmlTestReportGenerator.class, xmlResultsDir.getAsFile().toPath(), options);
        generator.generate(Collections.singletonList(binaryResultsDir.getAsFile().toPath()));
    }
}
