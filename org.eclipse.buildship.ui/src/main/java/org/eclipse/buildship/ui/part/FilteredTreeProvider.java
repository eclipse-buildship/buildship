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

import org.eclipse.buildship.ui.viewer.FilteredTree;

/**
 * Common interface for the part in order to access the {@link FilteredTree} and also directly its
 * underlying {@link org.eclipse.jface.viewers.TreeViewer}.
 */
public interface FilteredTreeProvider extends ViewerProvider {

    FilteredTree getFilteredTree();
}
