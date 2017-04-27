/*
 * Copyright (c) 2017 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.eclipse.buildship.core.workspace.internal;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildActionExecuter;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.LongRunningOperation;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ProgressListener;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.TestLauncher;

import com.gradleware.tooling.toolingmodel.repository.FixedRequestAttributes;
import com.gradleware.tooling.toolingmodel.repository.TransientRequestAttributes;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Platform;

import org.eclipse.buildship.core.CorePlugin;
import org.eclipse.buildship.core.GradlePluginsRuntimeException;
import org.eclipse.buildship.core.util.gradle.GradleDistributionWrapper;

/**
 * Provides long-running TAPI operation instances that close their project connection after the execution is
 * finished.
 *
 * @author Donat Csikos
 */
@SuppressWarnings("unchecked")
final class ConnectionAwareLauncherProxy implements InvocationHandler {

    private final LongRunningOperation launcher;
    private final ProjectConnection connection;
    private static URLClassLoader ideFriendlyCustomActionClassLoader;

    private ConnectionAwareLauncherProxy(ProjectConnection connection, LongRunningOperation target) {
        this.connection = connection;
        this.launcher = target;
    }

    static <T> ModelBuilder<T> newModelBuilder(Class<T> model, FixedRequestAttributes fixedAttributes, TransientRequestAttributes transientAttributes) {
        ProjectConnection connection = openConnection(fixedAttributes);
        ModelBuilder<T> builder = connection.model(model);
        applyRequestAttributes(builder, fixedAttributes, transientAttributes);
        return (ModelBuilder<T>) newProxyInstance(connection, builder);
    }

    static <T> BuildActionExecuter<Collection<T>> newCompositeModelQueryExecuter(Class<T> model, FixedRequestAttributes fixedAttributes, TransientRequestAttributes transientAttributes) {
        ProjectConnection connection = openConnection(fixedAttributes);
        BuildActionExecuter<Collection<T>> executer = connection.action(compositeModelQuery(model));
        applyRequestAttributes(executer, fixedAttributes, transientAttributes);
        return (BuildActionExecuter<Collection<T>>) newProxyInstance(connection, executer);
    }

    static BuildLauncher newBuildLauncher(FixedRequestAttributes fixedAttributes, TransientRequestAttributes transientAttributes) {
        ProjectConnection connection = openConnection(fixedAttributes);
        BuildLauncher launcher = connection.newBuild();
        applyRequestAttributes(launcher, fixedAttributes, transientAttributes);
        return (BuildLauncher) newProxyInstance(connection, launcher);
    }

    static TestLauncher newTestLauncher(FixedRequestAttributes fixedAttributes, TransientRequestAttributes transientAttributes) {
        ProjectConnection connection = openConnection(fixedAttributes);
        TestLauncher launcher = connection.newTestLauncher();
        applyRequestAttributes(launcher, fixedAttributes, transientAttributes);
        return (TestLauncher) newProxyInstance(connection, launcher);
    }

    private static ProjectConnection openConnection(FixedRequestAttributes fixedAttributes) {
        GradleConnector connector = GradleConnector.newConnector().forProjectDirectory(fixedAttributes.getProjectDir());
        GradleDistributionWrapper.from(fixedAttributes.getGradleDistribution()).apply(connector);
        connector.useGradleUserHomeDir(fixedAttributes.getGradleUserHome());
        return connector.connect();
    }

    private static void applyRequestAttributes(LongRunningOperation operation, FixedRequestAttributes fixedAttributes, TransientRequestAttributes transientAttributes) {
        operation.setJavaHome(fixedAttributes.getJavaHome());
        operation.withArguments(fixedAttributes.getArguments());
        operation.setJvmArguments(fixedAttributes.getJvmArguments());

        operation.setStandardOutput(transientAttributes.getStandardOutput());
        operation.setStandardError(transientAttributes.getStandardError());
        operation.setStandardInput(transientAttributes.getStandardInput());
        for (ProgressListener listener : transientAttributes.getProgressListeners()) {
            operation.addProgressListener(listener);
        }
        operation. withCancellationToken(transientAttributes.getCancellationToken());
    }

    private static <T> BuildAction<Collection<T>> compositeModelQuery(Class<T> model) {
        if (Platform.inDevelopmentMode()) {
            return ideFriendlyCompositeModelQuery(model);
        } else {
            return new CompositeModelQuery<>(model);
        }
    }

    private static <T> BuildAction<Collection<T>> ideFriendlyCompositeModelQuery(Class<T> model) {
        // When Buildship is launched from the IDE - as an Eclipse application or as a plugin-in
        // test - the URLs returned by the Equinox class loader is incorrect. This means, the
        // Tooling API is unable to find the referenced build actions and fails with a CNF
        // exception. To work around that, we look up the build action class locations and load the
        // classes via an isolated URClassLoader.
        try {
            ClassLoader coreClassloader = ConnectionAwareLauncherProxy.class.getClassLoader();
            ClassLoader tapiClassloader = ProjectConnection.class.getClassLoader();
            URL actionRootUrl = FileLocator.resolve(coreClassloader.getResource(""));
            ideFriendlyCustomActionClassLoader = new URLClassLoader(new URL[] { actionRootUrl }, tapiClassloader);
            Class<?> actionClass = ideFriendlyCustomActionClassLoader.loadClass(CompositeModelQuery.class.getName());
            return (BuildAction<Collection<T>>) actionClass.getConstructor(Class.class).newInstance(model);
        } catch (Exception e) {
            throw new GradlePluginsRuntimeException(e);
        }
    }

    private static Object newProxyInstance(ProjectConnection connection, LongRunningOperation launcher) {
        return Proxy.newProxyInstance(launcher.getClass().getClassLoader(),
                                      launcher.getClass().getInterfaces(),
                                      new ConnectionAwareLauncherProxy(connection, launcher));
    }

    @Override
    public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
        // BuildLauncher and TestLauncher have the same method signature for execution:
        // #run() and #run(ResultHandler)
        if (m.getName().equals("run") || m.getName().equals("get")) {
            if (args == null) {
                return invokeRun(m);
            } else if (args.length == 1 && args[0].getClass() == ResultHandler.class) {
                return invokeRun(m, args[0]);
            }
        }
        return invokeOther(m, args);
    }

    private Object invokeRun(Method m) throws Throwable {
        try {
            return m.invoke(this.launcher);
        } finally {
            closeConnection();
        }
    }

    private Object invokeRun(Method m, Object resultHandler) throws Throwable {
        final ResultHandler<Object> handler = (ResultHandler<Object>) resultHandler;
        return m.invoke(this.launcher, new ResultHandler<Object>() {

            @Override
            public void onComplete(Object result) {
                try {
                    handler.onComplete(result);
                } finally {
                    closeConnection();
                }
            }

            @Override
            public void onFailure(GradleConnectionException e) {
                try {
                    handler.onFailure(e);
                } finally {
                    closeConnection();
                }
            }
        });
    }

    private void closeConnection() {
        this.connection.close();
        if (ideFriendlyCustomActionClassLoader != null) {
            try {
                ideFriendlyCustomActionClassLoader.close();
            } catch (IOException e) {
                CorePlugin.logger().error("Can't close URL class loader", e);
            }
        }
    }

    private Object invokeOther(Method m, Object[] args) throws Throwable {
        return m.invoke(this.launcher, args);
    }
}
