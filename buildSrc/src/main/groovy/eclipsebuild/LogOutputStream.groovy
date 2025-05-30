/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Donát Csikós (Gradle Inc.) - initial API and implementation and initial documentation
 */

package eclipsebuild

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger

/**
 * Output stream forwarding the content of the stream to Gradle logging.
 */
class LogOutputStream extends ByteArrayOutputStream {

    enum Type {
        STDOUT(" | "),
        STDERR("e| ");

        final String prefix

        Type(String prefix) {
            this.prefix = prefix
        }
    }

    private final Logger logger
    private final LogLevel level
    private final Type type

    LogOutputStream(Logger logger, LogLevel level, Type type) {
        this.logger = logger
        this.level = level
        this.type = type
    }

    @Override
    void flush() {
        logger.log(level, toString().stripTrailing().replaceAll(/(?m)^/, type.prefix))
        reset()
    }
}
