/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Donát Csikós (Gradle Inc.) - initial API and implementation and initial documentation
 */

package eclipsebuild.testing;

import eclipsebuild.Constants;
import org.eclipse.jdt.internal.junit.model.ITestRunListener2;
import org.eclipse.jdt.internal.junit.model.RemoteTestRunnerClient;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.testing.*;
import org.gradle.api.internal.tasks.testing.detection.TestFrameworkDetector;
import org.gradle.api.internal.tasks.testing.processors.TestMainAction;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.time.Time;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.ExecFactory;
import org.gradle.process.internal.JavaExecAction;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class EclipseTestExecuter implements TestExecuter<TestExecutionSpec> {

    private static final Logger LOGGER = Logging.getLogger(EclipseTestExecuter.class);

    private final Project project;
    private final BuildOperationExecutor executor;

    public EclipseTestExecuter(Project project, BuildOperationExecutor executor) {
        this.project = project;
        this.executor = executor;
    }

    @Override
    public void execute(TestExecutionSpec test, TestResultProcessor testResultProcessor) {
        LOGGER.info("Executing tests in Eclipse");

        int pdeTestPort = new PDETestPortLocator().locatePDETestPortNumber();
        if (pdeTestPort == -1) {
            throw new GradleException("Cannot allocate port for PDE test run");
        }
        LOGGER.info("Will use port {} to communicate with Eclipse.", pdeTestPort);

        runPDETestsInEclipse(test, testResultProcessor, pdeTestPort);
    }

    private EclipseTestExtension getExtension(Test testTask) {
        return (EclipseTestExtension) testTask.getProject().getExtensions().findByName("eclipseTest");
    }

    private void runPDETestsInEclipse(final TestExecutionSpec testSpec, final TestResultProcessor testResultProcessor,
            final int pdeTestPort) {

        Test testTask = ((EclipseTestExecutionSpec)testSpec).getTestTask();
        Set<String> includePatterns = ((EclipseTestExecutionSpec)testSpec).getFilters();

        final Object testTaskOperationId = this.executor.getCurrentOperation().getParentId();
        final Object rootTestSuiteId = testTask.getPath();

        ExecutorService threadPool = Executors.newFixedThreadPool(2);
        File runDir = new File(testTask.getProject().getBuildDir(), testTask.getName());

        File testEclipseDir = new File(this.project.property("buildDir") + "/eclipseTest/eclipse");

        // File configIniFile = getInputs().getFiles().getSingleFile();
        File configIniFile = new File(testEclipseDir, "configuration/config.ini");
        assert configIniFile.exists();

        File runPluginsDir = new File(testEclipseDir, "plugins");
        LOGGER.info("Eclipse test directory is {}", runPluginsDir.getPath());
        File equinoxLauncherFile = getEquinoxLauncherFile(testEclipseDir);
        LOGGER.info("equinox launcher file {}", equinoxLauncherFile);

        ExecFactory execFactory = ((ProjectInternal) project).getServices().get(ExecFactory.class);
        WorkerLeaseService workerLeaseService = ((ProjectInternal) project).getServices().get(WorkerLeaseService.class);
        final JavaExecAction javaExecHandleBuilder = execFactory.newJavaExecAction();
        javaExecHandleBuilder.setClasspath(this.project.files(equinoxLauncherFile));
        javaExecHandleBuilder.getMainClass().set("org.eclipse.equinox.launcher.Main");
        javaExecHandleBuilder.setExecutable(testTask.getJavaLauncher().get().getExecutablePath().getAsFile());
        List<String> programArgs = new ArrayList<String>();

        programArgs.add("-os");
        programArgs.add(Constants.getOs());
        programArgs.add("-ws");
        programArgs.add(Constants.getWs());
        programArgs.add("-arch");
        programArgs.add(Constants.getArch());

        if (getExtension(testTask).isConsoleLog()) {
            programArgs.add("-consoleLog");
        }
        File optionsFile = getExtension(testTask).getOptionsFile();
        if (optionsFile != null) {
            programArgs.add("-debug");
            programArgs.add(optionsFile.getAbsolutePath());
        }
        programArgs.add("-version");
        programArgs.add("5");
        programArgs.add("-port");
        programArgs.add(Integer.toString(pdeTestPort));
        programArgs.add("-testLoaderClass");
        programArgs.add("org.eclipse.jdt.internal.junit5.runner.JUnit5TestLoader");
        programArgs.add("-loaderpluginname");
        programArgs.add("org.eclipse.jdt.junit5.runtime");
        programArgs.add("-classNames");

        List<String> testNames = new ArrayList(collectTestNames(testTask, testTaskOperationId, workerLeaseService));
        if (!includePatterns.isEmpty()) {
            Set<Pattern> patterns = includePatterns.stream().map( p -> Pattern.compile(".*" + p + ".*")).collect(Collectors.toSet());
            testNames = testNames.stream().filter( testName -> matches(testName, patterns)).collect(Collectors.toList());
        }
        Collections.sort(testNames);
        programArgs.addAll(testNames);

        programArgs.add("-application");
        programArgs.add(getExtension(testTask).getApplicationName());
        programArgs.add("-product org.eclipse.platform.ide");
        // alternatively can use URI for -data and -configuration (file:///path/to/dir/)
        programArgs.add("-data");
        programArgs.add(runDir.getAbsolutePath() + File.separator + "workspace");
        programArgs.add("-configuration");
        programArgs.add(configIniFile.getParentFile().getAbsolutePath());

        programArgs.add("-testpluginname");
        String fragmentHost = getExtension(testTask).getFragmentHost();
        if (fragmentHost != null) {
            programArgs.add(fragmentHost);
        } else {
            programArgs.add(this.project.getName());
        }

        javaExecHandleBuilder.setArgs(programArgs);
        javaExecHandleBuilder.setSystemProperties(testTask.getSystemProperties());
        javaExecHandleBuilder.setEnvironment(testTask.getEnvironment());

        // TODO this should be specified when creating the task (to allow override in build script)
        List<String> jvmArgs = new ArrayList<String>();
        jvmArgs.addAll(testTask.getJvmArgs());
        if (!testTask.getJavaLauncher().get().getMetadata().getLanguageVersion().canCompileOrRun(9)) {
            jvmArgs.add("-XX:MaxPermSize=1024m");
        }
        jvmArgs.add("-Xms40m");
        jvmArgs.add("-Xmx8192m");

        // Java 9 workaround from https://bugs.eclipse.org/bugs/show_bug.cgi?id=493761
        // TODO we should remove this option when it is not required by Eclipse
        if (testTask.getJavaLauncher().get().getMetadata().getLanguageVersion().canCompileOrRun(9)) {
            jvmArgs.add("--add-modules=ALL-SYSTEM");
        }
        // uncomment to debug spawned Eclipse instance
        // jvmArgs.add("-Xdebug");
        // jvmArgs.add("-Xrunjdwp:transport=dt_socket,address=8998,server=y");

        if (Constants.getOs().equals("macosx")) {
            jvmArgs.add("-XstartOnFirstThread");
        }

        // declare mirror urls if exists
        Map<String, String> mirrorUrls = new HashMap<>();
        if (project.hasProperty("mirrors")) {
            String mirrorsString = (String) project.property("mirrors");
            String[] mirrors = mirrorsString.split(",");
            for (String mirror : mirrors) {
                if (!"".equals(mirror)) {
                    String[] nameAndUrl = mirror.split(":", 2);
                    mirrorUrls.put(nameAndUrl[0], nameAndUrl[1]);
                }
            }
        }

        for (Map.Entry<String, String> mirrorUrl : mirrorUrls.entrySet()) {
            jvmArgs.add("-Dorg.eclipse.buildship.eclipsetest.mirrors." + mirrorUrl.getKey() + "=" + mirrorUrl.getValue());
        }

        javaExecHandleBuilder.setJvmArgs(jvmArgs);
        javaExecHandleBuilder.setWorkingDir(this.project.getBuildDir());

        final CountDownLatch latch = new CountDownLatch(1);
        Future<?> eclipseJob = threadPool.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    ExecResult execResult = javaExecHandleBuilder.execute();
                    execResult.assertNormalExitValue();
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
                finally {
                    latch.countDown();
                }
            }
        });
        // TODO
        final String suiteName = this.project.getName();

        EclipseTestListener pdeTestListener = new EclipseTestListener();
        RemoteTestRunnerClient remoteTestRunnerClient = new RemoteTestRunnerClient();
        remoteTestRunnerClient.startListening(new ITestRunListener2[] { pdeTestListener }, pdeTestPort);
        LOGGER.info("Listening on port " + pdeTestPort + " for test suite " + suiteName + " results ...");

        EclipseTestAdapter eclipseTestAdapter = new EclipseTestAdapter(pdeTestListener, new EclipseTestResultProcessor(testResultProcessor, suiteName, testTask, rootTestSuiteId, project.getLogger()));

        if(!eclipseTestAdapter.processEvents()) {
            throw new GradleException("Test execution failed");
        }

        try {
            eclipseJob.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new GradleException("Test execution failed", e);
        }
    }

    private boolean matches(String testName, Set<Pattern> patterns) {
        return patterns.stream().anyMatch(pattern -> pattern.matcher(testName).matches());
    }

    private File getEquinoxLauncherFile(File testEclipseDir) {
         File[] plugins = new File(testEclipseDir, "plugins").listFiles();
         for (File plugin : plugins) {
             if (plugin.getName().startsWith("org.eclipse.equinox.launcher_")) {
                 return plugin;
             }
         }
        return null;
    }

    private List<String> collectTestNames(Test testTask, Object testTaskOperationId, WorkerLeaseService workerLeaseService) {
        ClassNameCollectingProcessor processor = new ClassNameCollectingProcessor();
        Runnable detector;
        final FileTree testClassFiles = testTask.getCandidateClassFiles();
        if (testTask.isScanForTestClasses()) {
            TestFrameworkDetector testFrameworkDetector = testTask.getTestFramework().getDetector();
            testFrameworkDetector.setTestClasses(testTask.getTestClassesDirs().getFiles());
            testFrameworkDetector.setTestClasspath(testTask.getClasspath().getFiles());
            detector = new EclipsePluginTestClassScanner(testClassFiles, processor);
        } else {
            detector = new EclipsePluginTestClassScanner(testClassFiles, processor);
        }

        new TestMainAction(detector, processor, new NoOpTestResultProcessor(), workerLeaseService, Time.clock(), testTaskOperationId, String.format("Gradle Eclipse Test Run %s", testTask.getIdentityPath())).run();
        LOGGER.info("collected test class names: {}", processor.classNames);
        return processor.classNames;
    }

    @Override
    public void stopNow() {

    }
    public static final class NoOpTestResultProcessor implements TestResultProcessor {


        @Override
        public void started(TestDescriptorInternal testDescriptorInternal, TestStartEvent testStartEvent) {
        }

        @Override
        public void completed(Object o, TestCompleteEvent testCompleteEvent) {
        }

        @Override
        public void output(Object o, TestOutputEvent testOutputEvent) {
        }

        @Override
        public void failure(Object o, Throwable throwable) {
        }
    }

    private class ClassNameCollectingProcessor implements TestClassProcessor {
        public List<String> classNames = new ArrayList<String>();

        @Override
        public void startProcessing(TestResultProcessor testResultProcessor) {
            // no-op
        }

        @Override
        public void processTestClass(TestClassRunInfo testClassRunInfo) {
            this.classNames.add(testClassRunInfo.getTestClassName());
        }

        @Override
        public void stop() {
            // no-op
        }

        @Override
        public void stopNow() {
            // no-op
        }
    }
}
