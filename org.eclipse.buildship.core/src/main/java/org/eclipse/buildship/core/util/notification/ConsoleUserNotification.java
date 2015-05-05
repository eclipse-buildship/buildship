/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Etienne Studer & Donát Csikós (Gradle Inc.) - initial API and implementation and initial documentation
 */

package org.eclipse.buildship.core.util.notification;

import org.eclipse.buildship.core.UserNotification;

/**
 * Implementation of the {@link UserNotification} interface printing all events to the standard
 * error.
 */
public final class ConsoleUserNotification implements UserNotification {

    @Override
    public void notifyAboutException(String message, String summary, Exception exception) {
        System.err.println("User notification: message=[" + message + "], summary=[" + summary + "], exception=[" + exception + "]");
    }

}
