/*
 * Copyright (c) 2017 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.eclipse.buildship.ui.internal.util.widget;

import java.io.File;
import java.util.List;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;

import org.eclipse.debug.ui.StringVariableSelectionDialog;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

import org.eclipse.buildship.core.internal.i18n.CoreMessages;
import org.eclipse.buildship.ui.internal.i18n.UiMessages;
import org.eclipse.buildship.ui.internal.launch.LaunchMessages;
import org.eclipse.buildship.ui.internal.util.file.DirectoryDialogSelectionListener;
import org.eclipse.buildship.ui.internal.util.font.FontUtils;

/**
 * Composite to select advanced options such as the Gradle user home, the Java home, and program/JVM arguments.
 *
 * @author Donat Csikos
 */
public final class AdvancedOptionsGroup extends Group {

    private final Font defaultFont;
    private final UiBuilder.UiBuilderFactory builderFactory;

    private Text gradleUserHomeText;
    private Button gradleUserHomeBrowseButton;
    private Label gradleUserHomeWarningLabel;

    private Text javaHomeText;
    private Button javaHomeBrowseButton;
    private Label javaHomeWarningLabel;

    private Text argumentsText;
    private Text jvmArgumentsText;

    public AdvancedOptionsGroup(Composite parent, boolean variableSelector) {
        super(parent, SWT.NONE);
        setText(CoreMessages.Preference_Label_AdvancedOptions);

        this.defaultFont = FontUtils.getDefaultDialogFont();
        this.builderFactory = new UiBuilder.UiBuilderFactory(this.defaultFont);

        setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        setLayout(new GridLayout(4, false));

        this.builderFactory.newLabel(this).alignLeft().text(CoreMessages.Preference_Label_Gradle_User_Home);
        this.gradleUserHomeText = this.builderFactory.newText(this).alignFillHorizontal().control();
        this.gradleUserHomeBrowseButton = this.builderFactory.newButton(this).alignLeft().text(UiMessages.Button_Label_Browse).control();
        this.gradleUserHomeWarningLabel = this.builderFactory.newLabel(this).alignLeft().control();
        this.gradleUserHomeWarningLabel.setImage(PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJS_WARN_TSK));
        HoverText.createAndAttach(this.gradleUserHomeWarningLabel, NLS.bind(CoreMessages.WarningMessage_Using_0_NonPortable, "Gradle user home"));

        this.builderFactory.newLabel(this).alignLeft().text(CoreMessages.Preference_Label_Java_Home);
        this.javaHomeText = this.builderFactory.newText(this).alignFillHorizontal().control();
        this.javaHomeBrowseButton = this.builderFactory.newButton(this).alignLeft().text(UiMessages.Button_Label_Browse).control();
        this.javaHomeWarningLabel = this.builderFactory.newLabel(this).alignLeft().control();
        this.javaHomeWarningLabel.setImage(PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJS_WARN_TSK));
        HoverText.createAndAttach(this.gradleUserHomeWarningLabel, NLS.bind(CoreMessages.WarningMessage_Using_0_NonPortable, "Java home"));

        this.builderFactory.newLabel(this).alignLeft().text(CoreMessages.RunConfiguration_Label_Arguments);
        if (variableSelector) {
            this.argumentsText = createMultlineText(this);
            createVariablesSelectorButton(this, this.argumentsText);
        } else {
            this.argumentsText = this.builderFactory.newText(this).alignFillBoth(1).control();
            this.builderFactory.newLabel(this).control().setVisible(false);
        }
        this.builderFactory.newLabel(this).control().setVisible(false);

        this.builderFactory.newLabel(this).alignLeft().text(CoreMessages.RunConfiguration_Label_JvmArguments);
        if (variableSelector) {
            this.jvmArgumentsText = createMultlineText(this);
            createVariablesSelectorButton(this, this.jvmArgumentsText);
        } else {
            this.jvmArgumentsText = this.builderFactory.newText(this).alignFillBoth(1).control();
            this.builderFactory.newLabel(this).control().setVisible(false);
        }
        this.builderFactory.newLabel(this).control().setVisible(false);

        addListeners();
    }

    private void addListeners() {
        this.gradleUserHomeText.addModifyListener(l -> updateWarningVisibility());
        this.javaHomeText.addModifyListener(l -> updateWarningVisibility());
        this.gradleUserHomeBrowseButton.addSelectionListener(new DirectoryDialogSelectionListener(this.getShell(), this.gradleUserHomeText, "Gradle user home"));
        this.javaHomeBrowseButton.addSelectionListener(new DirectoryDialogSelectionListener(this.getShell(), this.javaHomeText, "Java home"));
    }

    private Text createMultlineText(Composite container) {
        Text textControl = new Text(container, SWT.MULTI | SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
        GridData textLayoutData = new GridData(SWT.FILL, SWT.TOP, true, false, 1, 1);
        textLayoutData.heightHint = 65;
        textControl.setLayoutData(textLayoutData);
        return textControl;
    }

    private void createVariablesSelectorButton(Composite container, final Text target) {
        Composite buttonContainer = new Composite(container, SWT.NONE);
        GridLayout buttonContainerLayout = new GridLayout(1, false);
        buttonContainerLayout.marginHeight = 1;
        buttonContainerLayout.marginWidth = 0;
        buttonContainer.setLayout(buttonContainerLayout);
        buttonContainer.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));

        Button selectVariableButton = new Button(buttonContainer, SWT.NONE);
        selectVariableButton.setText(LaunchMessages.Button_Label_SelectVariables);
        selectVariableButton.addSelectionListener(new SelectionAdapter() {

            @Override
            public void widgetSelected(SelectionEvent e) {
                StringVariableSelectionDialog dialog = new StringVariableSelectionDialog(getShell());
                dialog.open();
                String variable = dialog.getVariableExpression();
                if (variable != null) {
                    target.insert(variable);
                }
            }
        });
    }

    public Text getGradleUserHomeText() {
        return this.gradleUserHomeText;
    }

    public Text getJavaHomeText() {
        return this.javaHomeText;
    }


    public Text getArgumentsText() {
        return this.argumentsText;
    }


    public Text getJvmArgumentsText() {
        return this.jvmArgumentsText;
    }

    @Override
    protected void checkSubclass() {
        // Disable the check that prevents subclassing of SWT components
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        updateEnablement();
    }

    private void updateEnablement() {
        boolean groupEnabled = getEnabled();
        this.gradleUserHomeText.setEnabled(groupEnabled);
        this.gradleUserHomeBrowseButton.setEnabled(groupEnabled);
        this.javaHomeText.setEnabled(groupEnabled);
        this.javaHomeBrowseButton.setEnabled(groupEnabled);
        updateWarningVisibility();
    }

    private void updateWarningVisibility() {
        boolean layout = false;
        layout |= updateWarningVisibility(getEnabled(), this.gradleUserHomeText, this.gradleUserHomeWarningLabel);
        layout |= updateWarningVisibility(getEnabled(), this.javaHomeText, this.javaHomeWarningLabel);
        if (layout) {
            // the Control.requestLayout() method was introduced in Eclipse Neon
            getShell().layout(new Control[] { this }, SWT.DEFER);
        }
    }

    private static boolean updateWarningVisibility(boolean enabled, Text text, Label warning) {
        boolean warningIsVisible = warning.getVisible();
        boolean warningShouldBeVisible = enabled && !text.getText().isEmpty();
        if (warningIsVisible != warningShouldBeVisible) {
            warning.setVisible(warningShouldBeVisible);
            Object layoutData = warning.getLayoutData();
            if (layoutData instanceof GridData) {
                GridData gridData = (GridData) layoutData;
                gridData.widthHint = warningShouldBeVisible ? SWT.DEFAULT : 0;
                return true;
            }
        }
        return false;
    }

    public File getGradleUserHome() {
        String gradleUserHomeString = this.gradleUserHomeText.getText();
        return gradleUserHomeString.isEmpty() ? null : new File(gradleUserHomeString);
    }

    public void setGradleUserHome(File gradleUserHome) {
        if (gradleUserHome == null) {
            this.gradleUserHomeText.setText("");
        } else {
            this.gradleUserHomeText.setText(gradleUserHome.getPath());
        }
    }

    public File getJavaHome() {
        String javaHomeString = this.javaHomeText.getText();
        return javaHomeString.isEmpty() ? null : new File(javaHomeString);
    }

    public void setJavaHome(File javaHome) {
        if (javaHome == null) {
            this.javaHomeText.setText("");
        } else {
            this.javaHomeText.setText(javaHome.getPath());
        }
    }

    public List<String> getArguments() {
        return Splitter.on(' ').omitEmptyStrings().splitToList(this.argumentsText.getText());
    }


    public void setArguments(List<String> arguments) {
        this.argumentsText.setText(Joiner.on(' ').join(arguments));
    }

    public List<String> getJvmArguments() {
        return Splitter.on(' ').omitEmptyStrings().splitToList(this.jvmArgumentsText.getText());
    }

    public void setJvmArguments(List<String> jvmArguments) {
        this.jvmArgumentsText.setText(Joiner.on(' ').join(jvmArguments));
    }

    @Override
    public void dispose() {
        if (this.defaultFont != null && !this.defaultFont.isDisposed()) {
            this.defaultFont.dispose();
        }
        super.dispose();
    }
}
