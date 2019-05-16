/*
 * Copyright (c) 2019 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.eclipse.buildship.ui.internal.util.widget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.primitives.Ints;

import org.eclipse.debug.ui.StringVariableSelectionDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.TableEditor;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

import org.eclipse.buildship.ui.internal.launch.LaunchMessages;

public class ArgumentsEditor {

    private final Table table;
    private final TableEditor editor;
    private final List<StringListChangeListener> listeners = new ArrayList<>();
    private final Button addButton;
    private final Button variablesButton;

    public ArgumentsEditor(Composite parent, boolean variableSelector) {
        this.table = new Table(parent, SWT.FULL_SELECTION | SWT.HIDE_SELECTION | SWT.MULTI);
        GridDataFactory.swtDefaults().align(SWT.FILL, SWT.CENTER).grab(true, false).hint(SWT.DEFAULT, 65).applyTo(this.table);

        this.table.setLinesVisible(true);

        TableColumn column = new TableColumn(this.table, SWT.NONE);
        column.pack();
        column.setWidth(this.table.getClientArea().width);
        autoResizeColumnToTableWidth(column);

        this.editor = new TableEditor(this.table);
        this.editor.horizontalAlignment = SWT.LEFT;
        this.editor.grabHorizontal = true;
        hookEditoToTable();

        Composite buttonRoot = new Composite(parent, SWT.None);
        GridDataFactory.swtDefaults().span(2, 1).applyTo(buttonRoot);
        GridLayoutFactory.fillDefaults().margins(0, 0).spacing(0, 0).applyTo(buttonRoot);

        this.addButton = createButton(buttonRoot, "Add", () -> addArguments(Arrays.asList("arg")));
        if (variableSelector) {
            this.variablesButton = createButton(buttonRoot, LaunchMessages.Button_Label_SelectVariables, () -> {
                StringVariableSelectionDialog dialog = new StringVariableSelectionDialog(buttonRoot.getShell());
                dialog.open();
                String variable = dialog.getVariableExpression();
                if (variable != null) {
                    addArguments(Arrays.asList(variable));
                }
            });
        } else {
            this.variablesButton = null;
        }
    }

    private void autoResizeColumnToTableWidth(TableColumn column) {
        this.table.addControlListener(new ControlAdapter() {

            @Override
            public void controlResized(ControlEvent e) {
                column.setWidth(ArgumentsEditor.this.table.getClientArea().width);
            }
        });
    }

    private void hookEditoToTable() {
        this.table.addSelectionListener(new SelectionAdapter() {

            @Override
            public void widgetSelected(SelectionEvent e) {
                Control oldEditor = ArgumentsEditor.this.editor.getEditor();
                if (oldEditor != null) {
                    oldEditor.dispose();
                }

                TableItem item = (TableItem) e.item;
                if (item == null) {
                    return;
                }

                Text newEditor = new Text(ArgumentsEditor.this.table, SWT.NONE);
                newEditor.setText(item.getText(0));
                newEditor.addModifyListener(new ModifyListener() {

                    @Override
                    public void modifyText(ModifyEvent e) {
                        Text text = (Text) ArgumentsEditor.this.editor.getEditor();
                        ArgumentsEditor.this.editor.getItem().setText(0, text.getText());
                        notifyListeners();

                    }
                });

                newEditor.addFocusListener(new FocusListener() {

                    @Override
                    public void focusLost(FocusEvent e) {
                        String text = newEditor.getText();
                        if (text.isEmpty()) {
                            int index = ArgumentsEditor.this.table.indexOf(ArgumentsEditor.this.editor.getItem());
                            if (index >= 0) {
                                ArgumentsEditor.this.table.remove(index);
                                notifyListeners();
                            }
                        }
                    }

                    @Override
                    public void focusGained(FocusEvent e) {
                        // TODO Auto-generated method stub

                    }
                });
                newEditor.selectAll();
                newEditor.setFocus();
                ArgumentsEditor.this.editor.setEditor(newEditor, item, 0);
            }
        });
    }

    private Button createButton(Composite root, String label, Runnable onSelection) {
        Button button = new Button(root, SWT.PUSH);
        button.setText(label);
        GridDataFactory.swtDefaults().align(SWT.LEFT, SWT.FILL).applyTo(button);
        button.addSelectionListener(new SelectionAdapter() {

            @Override
            public void widgetSelected(SelectionEvent e) {
                onSelection.run();
            }
        });
        return button;
    }

    public void setArguments(List<String> items) {
        this.table.removeAll();
        addArguments(items);
    }

    public void addArguments(List<String> items) {
        for (String item : items) {
            TableItem tableItem = new TableItem(this.table, SWT.NONE);
            tableItem.setText(new String[] { item });
        }
        notifyListeners();
    }

    public List<String> getArguments() {
        return Arrays.stream(this.table.getItems()).map(ti -> ti.getText()).collect(Collectors.toList());
    }

    public void removeSelected() {
        List<Integer> indexes = Ints.asList(ArgumentsEditor.this.table.getSelectionIndices());
        Collections.sort(indexes, Collections.reverseOrder());
        for (Integer index : indexes) {
            ArgumentsEditor.this.table.remove(index);
            Control cellEditor = this.editor.getEditor();
            if (cellEditor != null && !cellEditor.isDisposed()) {
                this.editor.getEditor().setVisible(false);
            }
        }
        notifyListeners();
    }

    public void setLayoutData(Object layoutData) {
        this.table.setLayoutData(layoutData);
    }

    public Composite getComposite() {
        return this.table;
    }

    private void notifyListeners() {
        this.listeners.forEach(l -> l.onChange());
    }

    public void addChangeListener(StringListChangeListener listener) {
        this.listeners.add(listener);
    }

    @FunctionalInterface
    public static interface StringListChangeListener {

        void onChange();
    }

    public void setEnabled(boolean enabled) {
        this.table.setEnabled(enabled);
        this.addButton.setEnabled(enabled);
        if (this.variablesButton != null) {
            this.variablesButton.setEnabled(enabled);
        }
    }

}
