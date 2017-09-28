/*
 * Copyright (c) 2017 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.buildship.ui.editor;

import org.eclipse.jface.text.presentation.IPresentationReconciler;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.ui.editors.text.TextSourceViewerConfiguration;

import org.eclipse.buildship.ui.UiPlugin;

public class GradleTextViewerConfiguration extends TextSourceViewerConfiguration {
    /**
     *
     * @param editor the editor we're creating.
     * @param preferenceStore the preference store.
     */
    public GradleTextViewerConfiguration() {
        super(UiPlugin.getInstance().getPreferenceStore());
    }

    @Override
    public IPresentationReconciler getPresentationReconciler(ISourceViewer sourceViewer) {
        GradlePresentationReconciler reconciler = new GradlePresentationReconciler();
        reconciler.setDocumentPartitioning(getConfiguredDocumentPartitioning(sourceViewer));
        return reconciler;
    }

    @Override
    public String[] getConfiguredContentTypes(ISourceViewer sourceViewer) {
        return IGradlePartitions.PARTITIONS;
    }

     @Override
     public String getConfiguredDocumentPartitioning(ISourceViewer sourceViewer) {
         return IGradlePartitions.PARTITIONING;
     }
}
