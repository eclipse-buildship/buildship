/*******************************************************************************
 * Copyright (c) 2023 Gradle Inc. and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.ui.internal.view.task;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jface.wizard.IWizard;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.PageBook;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.wizards.IWizardDescriptor;

import org.eclipse.buildship.core.internal.GradlePluginsRuntimeException;
import org.eclipse.buildship.core.internal.workspace.FetchStrategy;
import org.eclipse.buildship.core.internal.workspace.SynchronizationJob;
import org.eclipse.buildship.ui.internal.UiPluginConstants;
import org.eclipse.buildship.ui.internal.util.layout.LayoutUtils;
import org.eclipse.buildship.ui.internal.util.nodeselection.NodeSelection;
import org.eclipse.buildship.ui.internal.util.nodeselection.NodeSelectionProvider;
import org.eclipse.buildship.ui.internal.util.nodeselection.SelectionHistoryManager;
import org.eclipse.buildship.ui.internal.util.widget.FilteredTree;
import org.eclipse.buildship.ui.internal.util.widget.PatternFilter;

/**
 * A view displaying the Gradle tasks of the Gradle projects in the workspace.
 */
public final class TaskView extends ViewPart implements NodeSelectionProvider {

    // view id declared in the plugin.xml
    public static final String ID = "org.eclipse.buildship.ui.views.taskview"; //$NON-NLS-1$

    private TaskViewState state;
    private UiContributionManager uiContributionManager;
    private SelectionHistoryManager selectionHistoryManager;

    private PageBook pages;
    private Link emptyInputPage;
    private Label errorInputPage;
    private Composite nonEmptyInputPage;
    private TreeViewer treeViewer;
    private FilteredTree filteredTree;

    @Override
    public void init(IViewSite site) throws PartInitException {
        super.init(site);

        // load the persisted state before we create any UI components that query for some state
        this.state = new TaskViewState();
        this.state.load();
    }

    @Override
    public void createPartControl(Composite parent) {
        // the top-level control changing its content depending on whether the content provider
        // contains task data to display or not
        this.pages = new PageBook(parent, SWT.NONE);

        // if there is no task data to display, show only a label
        this.emptyInputPage = new Link(this.pages, SWT.NONE);
        this.emptyInputPage.setText(TaskViewMessages.Label_No_Gradle_Projects);

        // if there is a problem loading the task data, show an error label
        this.errorInputPage = new Label(this.pages, SWT.NONE);
        this.errorInputPage.setText(TaskViewMessages.Label_Reload_Error);
        this.errorInputPage.setForeground(parent.getDisplay().getSystemColor(SWT.COLOR_RED));

        // if there is task data to display, show the task tree and the search label on the bottom
        this.nonEmptyInputPage = new Composite(this.pages, SWT.NONE);
        GridLayout gridLayout = new GridLayout(1, false);
        gridLayout.marginWidth = gridLayout.marginHeight = gridLayout.verticalSpacing = 0;
        this.nonEmptyInputPage.setLayout(gridLayout);

        // add tree with two columns
        this.filteredTree = new FilteredTree(this.nonEmptyInputPage, SWT.H_SCROLL | SWT.V_SCROLL | SWT.MULTI, new PatternFilter(true));
        this.filteredTree.setShowFilterControls(false);
        this.treeViewer = this.filteredTree.getViewer();
        this.treeViewer.getTree().setHeaderVisible(true);
        this.treeViewer.getControl().setLayoutData(new GridData(GridData.FILL_BOTH));

        // set filter, comparator, and content provider
        this.treeViewer.addFilter(TaskNodeViewerFilter.createFor(getState()));
        this.treeViewer.setComparator(TaskNodeViewerSorter.createFor(this.state));
        this.treeViewer.setContentProvider(new TaskViewContentProvider(this));

        TreeViewerColumn treeViewerNameColumn = new TreeViewerColumn(this.treeViewer, SWT.LEFT);
        treeViewerNameColumn.setLabelProvider(new DelegatingStyledCellLabelProvider(new TaskNameLabelProvider()));
        final TreeColumn taskNameColumn = treeViewerNameColumn.getColumn();
        taskNameColumn.setText(TaskViewMessages.Tree_Column_Name_Text);
        taskNameColumn.setWidth(LayoutUtils.ensureMinimumColumnWidth(this.state.getHeaderNameColumnWidth()));

        TreeViewerColumn treeViewerDescriptionColumn = new TreeViewerColumn(this.treeViewer, SWT.LEFT);
        treeViewerDescriptionColumn.setLabelProvider(new TaskDescriptionLabelProvider());
        final TreeColumn taskDescriptionColumn = treeViewerDescriptionColumn.getColumn();
        taskDescriptionColumn.setText(TaskViewMessages.Tree_Column_Description_Text);
        taskDescriptionColumn.setWidth(LayoutUtils.ensureMinimumColumnWidth(this.state.getHeaderDescriptionColumnWidth()));

        // open the import wizard if the empty input page link is selected
        this.emptyInputPage.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent event) {
                try {
                    IWizardDescriptor descriptor = PlatformUI.getWorkbench().getImportWizardRegistry().findWizard(UiPluginConstants.IMPORT_WIZARD_ID);
                    IWizard wizard = descriptor.createWizard();
                    WizardDialog dialog = new WizardDialog(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), wizard);
                    dialog.open();
                } catch (CoreException e) {
                    throw new GradlePluginsRuntimeException(e);
                }
            }
        });

        // when changed save the header width into the state
        taskNameColumn.addControlListener(new ControlAdapter() {

            @Override
            public void controlResized(ControlEvent e) {
                TaskView.this.state.setHeaderNameColumnWidth(taskNameColumn.getWidth());
            }
        });

        taskDescriptionColumn.addControlListener(new ControlAdapter() {
            @Override
            public void controlResized(ControlEvent e) {
                TaskView.this.state.setHeaderDescriptionColumnWidth(taskDescriptionColumn.getWidth());
            }
        });

        // manage the selection history as required for the task execution and let the
        // SelectionHistoryManager propagate the NodeSelection to the Workbench
        this.selectionHistoryManager = new SelectionHistoryManager(this.treeViewer);
        getSite().setSelectionProvider(this.selectionHistoryManager);


        // create toolbar actions, menu items, event listeners, etc.
        this.uiContributionManager = new UiContributionManager(this);
        this.uiContributionManager.wire();

        // set initial content (use fetch strategy FROM_CACHE_ONLY as we don't want to enforce
        // a potentially expensive sync operation just because a view was opened)
        reload(FetchStrategy.FROM_CACHE_ONLY);

        Job.getJobManager().addJobChangeListener(new JobChangeAdapter() {
            @Override
            public void done(IJobChangeEvent event) {
                if (event.getJob() instanceof SynchronizationJob) {
                    reload(FetchStrategy.FROM_CACHE_ONLY);
                }
            }
        });
    }

    /**
     * Triggers a refresh of the view.
     */
    public void refresh() {
        if (!this.treeViewer.getTree().isDisposed()) {
            this.treeViewer.refresh(true);
        }
    }

    /**
     * Updates the view to display the given content.
     * @param content the content, never null
     */
    public void setContent(TaskViewContent content) {
        if (!this.pages.isDisposed() && !this.treeViewer.getControl().isDisposed()) {
            this.pages.showPage(content.isEmpty() ? this.emptyInputPage : this.nonEmptyInputPage);
            this.treeViewer.setInput(content);
        }
    }

    /**
     * Reloads the task model in the background and updates this view once the reload is complete.
     * Can be safely called outside the UI thread.
     * @param fetchStrategy determines how to get the model being visualized from the cache
     */
    public void reload(FetchStrategy fetchStrategy) {
        new ReloadTaskViewJob(this, fetchStrategy).schedule();
    }

    @Override
    public void setFocus() {
        this.pages.setFocus();
    }

    public TaskViewState getState() {
        return this.state;
    }

    public TreeViewer getTreeViewer() {
        return this.treeViewer;
    }

    public FilteredTree getFilteredTree() {
        return this.filteredTree;
    }

    @Override
    public NodeSelection getSelection() {
        return this.selectionHistoryManager.getSelectionHistory();
    }

    @Override
    public void dispose() {
        if (this.state != null) {
            this.state.dispose();
        }
        if (this.selectionHistoryManager != null) {
            this.selectionHistoryManager.dispose();
        }
        if (this.uiContributionManager != null) {
            this.uiContributionManager.dispose();
        }
        super.dispose();
    }

}
