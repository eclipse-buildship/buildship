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

import org.eclipse.buildship.core.event.Event;

/**
 * This event is fired, when new ExecutionItems are created and added.
 *
 * @see org.eclipse.buildship.ui.part.execution.listener.ExecutionTestProgressListener
 */
public interface OperationItemCreatedEvent extends Event {

    OperationItem getElement();

    Object getSource();

}
