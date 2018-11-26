/*
 * Copyright (c) 2017 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */


package org.eclipse.buildship.ui.internal.preferences;

import com.google.common.base.Optional;
import com.google.common.base.Supplier;

import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;

import org.eclipse.buildship.core.internal.util.binding.Validator;

/**
 * Updates validation messages on the preference pages when the input changes.
 *
 * @author Donat Csikos
 */
final class ValidatingListener<T> implements ModifyListener {

    private final PreferencePage preferencePage;
    private final Supplier<T> target;
    private final Validator<T> validator;

    public ValidatingListener(PreferencePage preferencePage, Supplier<T> target, Validator<T> validator) {
        this.preferencePage = preferencePage;
        this.target = target;
        this.validator = validator;
    }

    @Override
    public void modifyText(ModifyEvent e) {
        T validatedValue = this.target.get();
        Optional<String> error = this.validator.validate(validatedValue);
        this.preferencePage.setValid(!error.isPresent());
        this.preferencePage.setErrorMessage(error.orNull());
    }
}
