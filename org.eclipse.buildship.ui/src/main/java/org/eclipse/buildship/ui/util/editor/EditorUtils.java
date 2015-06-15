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

package org.eclipse.buildship.ui.util.editor;

import org.eclipse.buildship.core.GradlePluginsRuntimeException;
import org.eclipse.buildship.ui.UiPlugin;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.ui.*;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.ITextEditor;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Contains helper methods related to interacting with the Eclipse editors.
 */
public final class EditorUtils {

    private EditorUtils() {
    }

    public static IEditorPart openInInternalEditor(IFile file, boolean activate) {
        IEditorDescriptor desc;
        try {
            desc = IDE.getEditorDescriptor(file);
        } catch (PartInitException e1) {
            // thrown if no editor can be found
            desc = null;
        }

        String editorId = getInternalEditorId(desc);

        try {
            IWorkbenchPage activePage = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
           return  IDE.openEditor(activePage, file, editorId, activate);
        } catch (PartInitException e) {
            String message = String.format("Cannot open file %s in editor.", file.getFullPath());
            UiPlugin.logger().error(message, e); //$NON-NLS-1$
            throw new GradlePluginsRuntimeException(message, e);
        }
    }

    public static IEditorPart openInInternalEditor(File file, boolean activate) {
        IEditorDescriptor desc;
        try {
            desc = IDE.getEditorDescriptor(file.getName());
        } catch (PartInitException e1) {
            // thrown if no editor can be found
            desc = null;
        }

        String editorId = getInternalEditorId(desc);

        try {
            IWorkbenchPage activePage = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
            return IDE.openEditor(activePage, file.toURI(), editorId, activate);
        } catch (PartInitException e) {
            String message = String.format("Cannot open file %s in editor.", file.getAbsolutePath());
            UiPlugin.logger().error(message, e); //$NON-NLS-1$
            throw new GradlePluginsRuntimeException(message, e);
        }
    }

    private static String getInternalEditorId(IEditorDescriptor desc) {
        String editorId;
        if (desc == null || !desc.isInternal()) {
            editorId = "org.eclipse.ui.DefaultTextEditor"; //$NON-NLS-1$
        } else {
            editorId = desc.getId();
        }
        return editorId;
    }

    public static void selectAndReveal(int offset, int length, IEditorPart editor, IFile file) {
        if (editor instanceof ITextEditor) {
            ITextEditor textEditor = (ITextEditor) editor;
            textEditor.selectAndReveal(offset, length);
        } else {
            showWithMarker(editor, file, offset, length);
        }
    }

    private static void showWithMarker(IEditorPart editor, IFile file, int offset, int length) {
        IMarker marker = null;
        try {
            Bundle bundle = FrameworkUtil.getBundle(EditorUtils.class);
            marker = file.createMarker(bundle.getSymbolicName() + "navigationmarker"); //$NON-NLS-1$
            Map<String, Integer> attributes = new HashMap<String, Integer>(4);
            attributes.put(IMarker.CHAR_START, offset);
            attributes.put(IMarker.CHAR_END, offset + length);
            marker.setAttributes(attributes);
            IDE.gotoMarker(editor, marker);
        } catch (CoreException e) {
            String message = String.format("Cannot set marker in file %s.", file.getFullPath());
            UiPlugin.logger().error(message, e); //$NON-NLS-1$
            throw new GradlePluginsRuntimeException(message, e);
        } finally {
            if (marker != null) {
                try {
                    marker.delete();
                } catch (CoreException e) {
                    // ignore
                }
            }
        }
    }


}
