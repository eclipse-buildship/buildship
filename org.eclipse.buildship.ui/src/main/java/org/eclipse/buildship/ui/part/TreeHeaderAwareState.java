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

package org.eclipse.buildship.ui.part;

/**
 * Implemented by classes that manage the (persisted) state of the tree header.
 */
public interface TreeHeaderAwareState {

    /**
     * Returns the current visibility state of the tree header.
     *
     * @return {@code true} if the tree header should be shown, {@code false} otherwise
     */
    boolean isShowTreeHeader();

    /**
     * Sets the new visibility state the tree header.
     *
     * @param showTreeHeader the new visibility state of the tree header
     */
    void setShowTreeHeader(boolean showTreeHeader);

}
