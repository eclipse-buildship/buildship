/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Simon Scholz (vogella GmbH) - initial API and implementation and initial documentation
 */

package org.eclipse.buildship.ui.part;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

/**
 * Interface for pages, which are shown inside an
 * {@link org.eclipse.buildship.ui.part.AbstractPagePart}.
 *
 */
public interface IPage {

    String getDisplayName();

    void setDisplayName(String displayName);

    void createPage(Composite parent);

    Control getPageControl();

    void setFocus();

    void dispose();
}
