/*
 * Copyright (c) 2018 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.eclipse.buildship.core.internal.util.gradle;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.ProjectConnection;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Platform;

import org.eclipse.buildship.core.internal.GradlePluginsRuntimeException;
import org.eclipse.buildship.core.internal.workspace.CompositeModelQuery;
import org.eclipse.buildship.core.internal.workspace.DefaultModelProvider;

/**
 * When Buildship is launched from the IDE - as an Eclipse application or as a plug-in
 * test - the URLs returned by the Equinox class loader is incorrect. This means, the
 * Tooling API is unable to find the referenced build actions and fails with a CNF
 * exception. To work around that, we look up the build action class locations and load the
 * classes via an isolated URClassLoader.
 */
public class IdeFriendlyClassLoading {

    private static URLClassLoader classLoader;

    private IdeFriendlyClassLoading() {
    }

    @SuppressWarnings("unchecked")
    public static <T> BuildAction<Collection<T>> loadCompositeModelQuery(Class<T> model) {
        try {
            if (Platform.inDevelopmentMode()) {
                Class<?> actionClass = loadClassWithIdeFriendlyClassLoader(CompositeModelQuery.class.getName());
                return (BuildAction<Collection<T>>) actionClass.getConstructor(Class.class).newInstance(model);
            } else {
                return new CompositeModelQuery<>(model);
            }
        } catch (Exception e) {
            throw new GradlePluginsRuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T loadClass(Class<T> cls) {
        try {
            if (Platform.inDevelopmentMode()) {
                return (T) loadClassWithIdeFriendlyClassLoader(cls.getName()).newInstance();
            } else {
                return cls.newInstance();
            }
        } catch (Exception e) {
            throw new GradlePluginsRuntimeException(e);
        }
    }

    /**
     * Closes IDE-friendly class loader.
     * <p>
     * This method does not do anything if Eclipse is not in development mode.
     */
    public static void cleanup() {
        if (classLoader != null) {
            try {
                classLoader.close();
            } catch (IOException e) {
                throw new GradlePluginsRuntimeException(e);
            }
            classLoader = null;
        }
    }

    private static final Class<?> loadClassWithIdeFriendlyClassLoader(String classname) throws Exception {
        ClassLoader coreClassloader = DefaultModelProvider.class.getClassLoader();
        ClassLoader tapiClassloader = ProjectConnection.class.getClassLoader();
        URL actionRootUrl = FileLocator.resolve(coreClassloader.getResource(""));

        if (classLoader == null) {
            classLoader = new URLClassLoader(new URL[] { actionRootUrl }, tapiClassloader);
        }
        return classLoader.loadClass(classname);
    }
}
