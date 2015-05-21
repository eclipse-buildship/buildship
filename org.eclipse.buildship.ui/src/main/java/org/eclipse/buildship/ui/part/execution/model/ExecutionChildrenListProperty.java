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

package org.eclipse.buildship.ui.part.execution.model;

import org.eclipse.buildship.ui.view.execution.OperationItem;
import org.eclipse.core.databinding.beans.BeanProperties;
import org.eclipse.core.databinding.property.list.DelegatingListProperty;
import org.eclipse.core.databinding.property.list.IListProperty;

/**
 * <p>
 * This DelegatingListProperty is used for the databinding of the ExecutionItem model and the
 * TreeViewer. It specifies that the {@link org.eclipse.buildship.ui.view.execution.OperationItem#FIELD_CHILDREN} are the children, which
 * should be shown as children of the ExecutionItem in the TreeViewer.
 * </p>
 * <p>
 * In case there will also be other objects in the TreeViewer, which have a different property for
 * the children, it can be added here.
 * </p>
 */
public class ExecutionChildrenListProperty extends DelegatingListProperty {

    @Override
    protected IListProperty doGetDelegate(Object source) {
        if (source instanceof OperationItem) {
            return BeanProperties.list(OperationItem.class, OperationItem.FIELD_CHILDREN);
        }
        return null;
    }
}
