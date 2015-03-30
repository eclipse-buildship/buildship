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

package com.gradleware.tooling.eclipse.ui.launch;

import java.io.File;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.ui.AbstractLaunchConfigurationTab;
import org.eclipse.debug.ui.StringVariableSelectionDialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.ListDialog;
import org.eclipse.ui.model.WorkbenchLabelProvider;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.gradleware.tooling.eclipse.core.CorePlugin;
import com.gradleware.tooling.eclipse.core.gradle.GradleConnectionValidators;
import com.gradleware.tooling.eclipse.core.i18n.CoreMessages;
import com.gradleware.tooling.eclipse.core.launch.GradleRunConfigurationAttributes;
import com.gradleware.tooling.eclipse.core.project.GradleProjectNatures;
import com.gradleware.tooling.eclipse.core.util.collections.CollectionsUtils;
import com.gradleware.tooling.eclipse.core.util.file.FileUtils;
import com.gradleware.tooling.eclipse.core.util.variable.ExpressionUtils;
import com.gradleware.tooling.eclipse.ui.PluginImage.ImageState;
import com.gradleware.tooling.eclipse.ui.PluginImages;
import com.gradleware.tooling.eclipse.ui.util.file.DirectoryDialogSelectionListener;
import com.gradleware.tooling.toolingutils.binding.Validator;

/**
 * Specifies a root project and a list of tasks to execute via the run configurations.
 */
public final class ProjectTab extends AbstractLaunchConfigurationTab {

    private final Validator<File> workingDirValidator;

    private Text tasksText;
    private Text workingDirectoryText;
    private Button visualizeTestProgressCheckbox;

    public ProjectTab() {
        this.workingDirValidator = GradleConnectionValidators.requiredDirectoryValidator(CoreMessages.RunConfiguration_Label_WorkingDirectory);
    }

    @Override
    public String getName() {
        return LaunchMessages.Tab_Name_GradleTasks;
    }

    @Override
    public Image getImage() {
        return PluginImages.TASK.withState(ImageState.ENABLED).getImage();
    }

    @Override
    public void createControl(Composite root) {
        Composite parent = new Composite(root, SWT.NONE);
        GridLayout layout = new GridLayout(1, false);
        parent.setLayout(layout);
        setControl(parent);

        Group tasksGroup = createGroup(parent, CoreMessages.RunConfiguration_Label_GradleTasks + ":"); //$NON-NLS-1$
        createTasksSelectionControl(tasksGroup);

        Group workingDirectoryGroup = createGroup(parent, CoreMessages.RunConfiguration_Label_WorkingDirectory + ":"); //$NON-NLS-1$
        createWorkingDirectorySelectionControl(workingDirectoryGroup);

        Group progressVisualizationGroup = createGroup(parent, CoreMessages.RunConfiguration_Label_ProgressVisualization + ":"); //$NON-NLS-1$
        createProgressVisualizationControl(progressVisualizationGroup);
    }

    private Group createGroup(Composite parent, String groupName) {
        Group group = new Group(parent, SWT.NONE);
        group.setText(groupName);
        group.setLayout(new GridLayout());
        group.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        return group;
    }

    private void createTasksSelectionControl(Composite container) {
        this.tasksText = new Text(container, SWT.MULTI | SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
        GridData tasksTextLayoutData = new GridData(SWT.FILL, SWT.TOP, true, false, 1, 1);
        tasksTextLayoutData.heightHint = 50;
        this.tasksText.setLayoutData(tasksTextLayoutData);
        this.tasksText.addModifyListener(new ModifyListener() {

            @Override
            public void modifyText(ModifyEvent e) {
                updateLaunchConfigurationDialog();
            }
        });
    }

    private void createWorkingDirectorySelectionControl(Composite container) {
        this.workingDirectoryText = new Text(container, SWT.SINGLE | SWT.BORDER);
        this.workingDirectoryText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        this.workingDirectoryText.addModifyListener(new ModifyListener() {

            @Override
            public void modifyText(ModifyEvent event) {
                updateLaunchConfigurationDialog();
            }
        });

        Composite buttonContainer = new Composite(container, SWT.NONE);
        GridLayout buttonContainerLayout = new GridLayout(3, false);
        buttonContainerLayout.marginHeight = 1;
        buttonContainerLayout.marginWidth = 0;
        buttonContainer.setLayout(buttonContainerLayout);
        buttonContainer.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));

        Button browseWorkspaceButton = new Button(buttonContainer, SWT.NONE);
        browseWorkspaceButton.setText(LaunchMessages.Button_Label_BrowseWorkspace);
        browseWorkspaceButton.addSelectionListener(new SelectionAdapter() {

            @Override
            public void widgetSelected(SelectionEvent e) {
                ListDialog projectDialog = new ListDialog(getShell());
                projectDialog.setTitle(LaunchMessages.Title_BrowseWorkspaceDialog);
                projectDialog.setContentProvider(new ArrayContentProvider() {

                    @Override
                    public Object[] getElements(Object input) {
                        return findAllGradleProjects();
                    }

                });
                projectDialog.setLabelProvider(new WorkbenchLabelProvider());
                projectDialog.setInput(ResourcesPlugin.getWorkspace().getRoot());

                // open the dialog and put the path of the selected project into the working directory text field
                projectDialog.open();
                Object[] result = projectDialog.getResult();
                if (result != null) {
                    if (result.length > 0) {
                        String locationInExpression = ExpressionUtils.encodeWorkspaceLocation((IProject) result[0]);
                        ProjectTab.this.workingDirectoryText.setText(locationInExpression);
                    } else {
                        ProjectTab.this.workingDirectoryText.setText(""); //$NON-NLS-1$
                    }
                }
            }
        });

        Button browseFilesystemButton = new Button(buttonContainer, SWT.NONE);
        browseFilesystemButton.setText(LaunchMessages.Button_Label_BrowseFilesystem);
        browseFilesystemButton.addSelectionListener(new DirectoryDialogSelectionListener(getShell(), this.workingDirectoryText, LaunchMessages.Title_BrowseFileSystemDialog));

        Button selectVariableButton = new Button(buttonContainer, SWT.NONE);
        selectVariableButton.setText(LaunchMessages.Button_Label_SelectVariables);
        selectVariableButton.addSelectionListener(new SelectionAdapter() {

            @Override
            public void widgetSelected(SelectionEvent e) {
                StringVariableSelectionDialog dialog = new StringVariableSelectionDialog(getShell());
                dialog.open();
                String variable = dialog.getVariableExpression();
                if (variable != null) {
                    ProjectTab.this.workingDirectoryText.insert(variable);
                }
            }
        });
    }

    private IProject[] findAllGradleProjects() {
        return FluentIterable.from(CorePlugin.workspaceOperations().getAllProjects()).filter(new Predicate<IProject>() {

            @Override
            public boolean apply(IProject project) {
                return project.isOpen() && GradleProjectNatures.DEFAULT_NATURE.isPresentOn(project);
            }
        }).toArray(IProject.class);
    }

    private void createProgressVisualizationControl(Composite container) {
        this.visualizeTestProgressCheckbox = new Button(container, SWT.CHECK);
        this.visualizeTestProgressCheckbox.setText(CoreMessages.ProgressVisualization_Label_VisualizeTestProgress);
        this.visualizeTestProgressCheckbox.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                updateLaunchConfigurationDialog();
            }
        });
    }

    @Override
    public void initializeFrom(ILaunchConfiguration configuration) {
        GradleRunConfigurationAttributes configurationAttributes = GradleRunConfigurationAttributes.from(configuration);
        this.tasksText.setText(CollectionsUtils.joinWithSpace(configurationAttributes.getTasks()));
        this.workingDirectoryText.setText(Strings.nullToEmpty(configurationAttributes.getWorkingDirExpression()));
        this.visualizeTestProgressCheckbox.setSelection(configurationAttributes.isVisualizeTestProgress());
    }

    @Override
    public void performApply(ILaunchConfigurationWorkingCopy configuration) {
        GradleRunConfigurationAttributes.applyTasks(CollectionsUtils.splitBySpace(this.tasksText.getText()), configuration);
        GradleRunConfigurationAttributes.applyWorkingDirExpression(this.workingDirectoryText.getText(), configuration);
        GradleRunConfigurationAttributes.applyVisualizeTestProgress(this.visualizeTestProgressCheckbox.getSelection(), configuration);
    }

    @SuppressWarnings("Contract")
    @Override
    public boolean isValid(ILaunchConfiguration configuration) {
        String workingDirectoryExpression = this.workingDirectoryText.getText();

        String workingDirectoryResolved;
        try {
            workingDirectoryResolved = ExpressionUtils.decode(workingDirectoryExpression);
        } catch (CoreException e) {
            setErrorMessage(String.format(LaunchMessages.ErrorMessage_CannotResolveExpression_0, workingDirectoryExpression));
            return false;
        }

        File workingDir = FileUtils.getAbsoluteFile(workingDirectoryResolved).orNull();
        Optional<String> error = this.workingDirValidator.validate(workingDir);
        setErrorMessage(error.orNull());
        return !error.isPresent();
    }

    @Override
    public void setDefaults(ILaunchConfigurationWorkingCopy configuration) {
        // leave the controls empty
    }

}
