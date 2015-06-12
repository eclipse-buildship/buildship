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

package org.eclipse.buildship.ui.notification;

import java.io.PrintWriter;
import java.io.StringWriter;

import com.google.common.base.Preconditions;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.window.SameShellProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

import org.eclipse.buildship.core.GradlePluginsRuntimeException;
import org.eclipse.buildship.ui.i18n.UiMessages;
import org.eclipse.buildship.ui.util.font.FontUtils;

/**
 * Custom {@link Dialog} implementation showing an exception and its stacktrace.
 */
public final class ExceptionDetailsDialog extends Dialog {

    public static final int COPY_ERROR_BUTTON_ID = 25;
    private static final String LINE_SEPARATOR = System.getProperty("line.separator"); //$NON-NLS-1$

    private final Image image;
    private final String title;
    private final String message;
    private final String details;
    private final Throwable throwable;

    private Button detailsButton;
    private Control stackTraceArea;
    private Button copyErrorButton;

    private Clipboard clipboard;

    public ExceptionDetailsDialog(Shell shell, String title, String message, String details, int severity, Throwable throwable) {
        super(new SameShellProvider(shell));

        this.image = getIconForSeverity(severity, shell);
        this.title = Preconditions.checkNotNull(title);
        this.message = Preconditions.checkNotNull(message);
        this.details = Preconditions.checkNotNull(details);
        this.throwable = Preconditions.checkNotNull(throwable);

        setShellStyle(SWT.DIALOG_TRIM | SWT.RESIZE | SWT.APPLICATION_MODAL);
    }

    private Image getIconForSeverity(int severity, Shell shell) {
        int swtImageKey;
        switch (severity) {
            case IStatus.OK:
            case IStatus.INFO:
                swtImageKey = SWT.ICON_INFORMATION;
                break;
            case IStatus.WARNING:
            case IStatus.CANCEL:
                swtImageKey = SWT.ICON_WARNING;
                break;
            case IStatus.ERROR:
                swtImageKey = SWT.ICON_ERROR;
                break;
            default:
                throw new GradlePluginsRuntimeException("Can't find image for severity: " + severity);
        }

        return shell.getDisplay().getSystemImage(swtImageKey);
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        // set dialog box title
        shell.setText(this.title);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite container = (Composite) super.createDialogArea(parent);
        container.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        // dialog image
        ((GridLayout) container.getLayout()).numColumns = 2;
        Label imageLabel = new Label(container, 0);
        this.image.setBackground(imageLabel.getBackground());
        imageLabel.setImage(this.image);
        imageLabel.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_CENTER | GridData.VERTICAL_ALIGN_BEGINNING));

        // composite to include all text widgets
        Composite textArea = new Composite(container, SWT.NONE);
        GridLayout textAreaLayout = new GridLayout(1, false);
        textAreaLayout.verticalSpacing = FontUtils.getFontHeightInPixels(parent.getFont());
        textArea.setLayout(textAreaLayout);
        GridData textAreaLayoutData = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1);
        textAreaLayoutData.widthHint = convertHorizontalDLUsToPixels(IDialogConstants.MINIMUM_MESSAGE_AREA_WIDTH);
        textArea.setLayoutData(textAreaLayoutData);

        // message label
        Label messageLabel = new Label(textArea, SWT.WRAP);
        messageLabel.setText(this.message);
        GridData messageLabelGridData = new GridData();
        messageLabelGridData.verticalAlignment = SWT.TOP;
        messageLabelGridData.grabExcessHorizontalSpace = true;
        messageLabel.setLayoutData(messageLabelGridData);

        // details label
        Label detailsLabel = new Label(textArea, SWT.WRAP);
        detailsLabel.setText(this.details);
        GridData detailsLabelGridData = new GridData();
        detailsLabelGridData.verticalAlignment = SWT.TOP;
        detailsLabelGridData.grabExcessHorizontalSpace = true;
        detailsLabel.setLayoutData(detailsLabelGridData);

        return container;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, false);
        this.detailsButton = createButton(parent, IDialogConstants.DETAILS_ID, IDialogConstants.SHOW_DETAILS_LABEL, false);
        this.copyErrorButton = createButton(parent, COPY_ERROR_BUTTON_ID, "", false); //$NON-NLS-1$
        this.copyErrorButton.setToolTipText(UiMessages.ExceptionDetailsDialog_Copy_Error_Button_Tooltip);
        this.copyErrorButton.setImage(PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_TOOL_COPY));
    }

    @Override
    protected void setButtonLayoutData(Button button) {
        if (button.getData() != null && button.getData().equals(COPY_ERROR_BUTTON_ID)) {
            // do not set a width hint for the copy error button, like it is done in the super
            // implementation
            GridDataFactory.swtDefaults().applyTo(button);
            return;
        }
        super.setButtonLayoutData(button);
    }

    @Override
    protected void initializeBounds() {
        Composite buttonBar = (Composite) getButtonBar();
        GridLayout layout = (GridLayout) buttonBar.getLayout();
        // do not make columns equal width so that we can have a smaller copy error button
        layout.makeColumnsEqualWidth = false;
        super.initializeBounds();
    }

    @Override
    protected void buttonPressed(int id) {
        if (id == IDialogConstants.DETAILS_ID) {
            toggleStacktraceArea();
        } else if (id == COPY_ERROR_BUTTON_ID) {
            copyErrorToClipboard();
        } else {
            super.buttonPressed(id);
        }
    }

    private void copyErrorToClipboard() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.message);
        sb.append(LINE_SEPARATOR);
        sb.append(this.details);
        sb.append(LINE_SEPARATOR);
        sb.append(getStackTrace());

        getClipboard().setContents(new String[] { sb.toString() }, new Transfer[] { TextTransfer.getInstance() });
    }

    private void toggleStacktraceArea() {
        // show/hide stacktrace
        if (this.stackTraceArea == null) {
            this.stackTraceArea = createStacktraceArea((Composite) getContents());
            this.detailsButton.setText(IDialogConstants.HIDE_DETAILS_LABEL);
        } else {
            this.stackTraceArea.dispose();
            this.stackTraceArea = null;
            this.detailsButton.setText(IDialogConstants.SHOW_DETAILS_LABEL);
        }

        // compute the new window size
        Point oldSize = getContents().getSize();
        Point newSize = getContents().computeSize(SWT.DEFAULT, SWT.DEFAULT);

        Point oldWindowSize = getShell().getSize();
        Point newWindowSize = new Point(oldWindowSize.x, oldWindowSize.y + (newSize.y - oldSize.y));

        // crop new window size to screen
        Point windowLocation = getShell().getLocation();
        Rectangle screenArea = getContents().getDisplay().getClientArea();
        if (newWindowSize.y > screenArea.height - (windowLocation.y - screenArea.y)) {
            newWindowSize.y = screenArea.height - (windowLocation.y - screenArea.y);
        }

        getShell().setSize(newWindowSize);
        ((Composite) getContents()).layout();
    }

    private Control createStacktraceArea(Composite parent) {
        // create the stacktrace container area
        Composite container = new Composite(parent, SWT.NONE);
        container.setLayoutData(new GridData(GridData.FILL_BOTH));
        GridLayout containerLayout = new GridLayout();
        containerLayout.marginHeight = containerLayout.marginWidth = 0;
        container.setLayout(containerLayout);

        // create stacktrace content
        Text text = new Text(container, SWT.MULTI | SWT.READ_ONLY | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
        text.setLayoutData(new GridData(GridData.FILL_BOTH));

        // set stacktrace string to the Text control
        text.setText(getStackTrace());

        return container;
    }

    private String getStackTrace() {
        StringWriter writer = new StringWriter(1000);
        this.throwable.printStackTrace(new PrintWriter(writer));

        return writer.toString();
    }

    private Clipboard getClipboard() {
        if (this.clipboard == null) {
            this.clipboard = new Clipboard(getShell().getDisplay());
        }
        return this.clipboard;
    }

    @Override
    public boolean close() {
        if (this.clipboard != null) {
            this.clipboard.dispose();
            this.clipboard = null;
        }
        return super.close();
    }
}
