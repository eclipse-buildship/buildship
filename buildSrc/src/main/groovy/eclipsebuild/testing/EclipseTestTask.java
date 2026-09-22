package eclipsebuild.testing;

import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import eclipsebuild.Constants;
import eclipsebuild.LogOutputStream;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;
import org.eclipse.jdt.internal.junit.model.ITestRunListener2;
import org.eclipse.jdt.internal.junit.model.RemoteTestRunnerClient;
import org.gradle.api.GradleException;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.EmptyFileVisitor;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.testing.TestEventReporterFactory;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.ExecFactory;
import org.gradle.process.internal.JavaExecAction;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

public abstract class EclipseTestTask extends JavaExec {

    private static final Logger LOGGER = Logging.getLogger(EclipseTestTask.class);

    private static final String BINARY_RESULTS_DIR_NAME = "binary";

    @Inject
    protected abstract TestEventReporterFactory getTestEventReporterFactory();

    @Inject
    protected abstract ExecFactory getExecFactory();

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    @OutputDirectory
    public abstract DirectoryProperty getTestEclipseDirectory();

    @TaskAction
    @Override
    public void exec() {
        LOGGER.info("Executing tests in Eclipse");
        int pdeTestPort = new PDETestPortLocator().locatePDETestPortNumber();
        if (pdeTestPort == -1) {
            throw new GradleException("Cannot allocate port for PDE test run");
        }
        LOGGER.info("Will use port {} to communicate with Eclipse.", pdeTestPort);
        runPDETestsInEclipse(pdeTestPort);
    }

    private void runPDETestsInEclipse(final int pdeTestPort) {

        ExecutorService threadPool = Executors.newFixedThreadPool(2);
        File runDir = getProject().getLayout().getBuildDirectory().dir(getName()).get().getAsFile();

        File configIniFile = new File(getTestEclipseDirectory().getAsFile().get(), "configuration/config.ini");
        assert configIniFile.exists();

        File runPluginsDir = new File(getTestEclipseDirectory().getAsFile().get(), "plugins");
        LOGGER.info("Eclipse test directory is {}", runPluginsDir.getPath());
        File equinoxLauncherFile = getEquinoxLauncherFile(getTestEclipseDirectory().getAsFile().get());
        LOGGER.info("equinox launcher file {}", equinoxLauncherFile);

        final JavaExecAction javaExecHandleBuilder = getExecFactory().newJavaExecAction();
        javaExecHandleBuilder.setClasspath(this.getProject().files(equinoxLauncherFile));
        javaExecHandleBuilder.getMainClass().set("org.eclipse.equinox.launcher.Main");

        javaExecHandleBuilder.setExecutable(getJavaLauncher().get().getExecutablePath().getAsFile());
        List<String> programArgs = new ArrayList<String>();

        programArgs.add("-os");
        programArgs.add(Constants.getOs());
        programArgs.add("-ws");
        programArgs.add(Constants.getWs());
        programArgs.add("-arch");
        programArgs.add(Constants.getArch());

        if (getExtension(this).isConsoleLog()) {
            programArgs.add("-consoleLog");
        }
        File optionsFile = getExtension(this).getOptionsFile();
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

        List<String> testNames = new ArrayList<>(collectTestNames(getClasspath().getAsFileTree()));
        Collections.sort(testNames);
        programArgs.addAll(testNames);

        programArgs.add("-application");
        programArgs.add(getExtension(this).getApplicationName());
        programArgs.add("-product org.eclipse.platform.ide");
        // alternatively can use URI for -data and -configuration (file:///path/to/dir/)
        programArgs.add("-data");
        programArgs.add(runDir.getAbsolutePath() + File.separator + "workspace");
        programArgs.add("-configuration");
        programArgs.add(configIniFile.getParentFile().getAbsolutePath());

        programArgs.add("-testpluginname");
        String fragmentHost = getExtension(this).getFragmentHost();
        if (fragmentHost != null) {
            programArgs.add(fragmentHost);
        } else {
            programArgs.add(getProject().getName());
        }

        javaExecHandleBuilder.setArgs(programArgs);
        javaExecHandleBuilder.setSystemProperties(getSystemProperties());
        javaExecHandleBuilder.setEnvironment(getEnvironment());

        // TODO this should be specified when creating the task (to allow override in build script)
        List<String> jvmArgs = new ArrayList<String>();
        jvmArgs.addAll(getJvmArgs());
        if (!getJavaLauncher().get().getMetadata().getLanguageVersion().canCompileOrRun(9)) {
            jvmArgs.add("-XX:MaxPermSize=1024m");
        }
        jvmArgs.add("-Xms40m");
        jvmArgs.add("-Xmx8192m");

        // Java 9 workaround from https://bugs.eclipse.org/bugs/show_bug.cgi?id=493761
        // TODO we should remove this option when it is not required by Eclipse
        if (getJavaLauncher().get().getMetadata().getLanguageVersion().canCompileOrRun(9)) {
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
        if (this.getProject().hasProperty("mirrors")) {
            String mirrorsString = (String) this.getProject().property("mirrors");
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
        javaExecHandleBuilder.setWorkingDir(getProject().getLayout().getBuildDirectory().getAsFile());
        javaExecHandleBuilder.setStandardOutput(new LogOutputStream(getLogger(), LogLevel.INFO, LogOutputStream.Type.STDOUT));
        javaExecHandleBuilder.setErrorOutput(new LogOutputStream(getLogger(), LogLevel.INFO, LogOutputStream.Type.STDERR));

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
        EclipseTestListener pdeTestListener = new EclipseTestListener();
        RemoteTestRunnerClient remoteTestRunnerClient = new RemoteTestRunnerClient();
        remoteTestRunnerClient.startListening(new ITestRunListener2[] { pdeTestListener }, pdeTestPort);
        LOGGER.info("Listening on port {} for Eclipse Integration Test results in project {}...", pdeTestPort, getProject().getName());

        Directory testResultsDir = cleanDirectory("test-results/" + getName());
        Directory binaryResultsDir = testResultsDir.dir(BINARY_RESULTS_DIR_NAME);
        EclipseTestAdapter eclipseTestAdapter = new EclipseTestAdapter(
                pdeTestListener,
                getTestEventReporterFactory().createTestEventReporter(
                    "Eclipse Integration Test",
                        binaryResultsDir,
                        cleanDirectory("reports/tests/" + getName())
                )
        );

        boolean success;
        try {
            success = eclipseTestAdapter.processEvents();
        } finally {
            JUnitXmlReport.generate(getObjectFactory(), binaryResultsDir, testResultsDir);
            LOGGER.info("JUnit XML test results written to {}", testResultsDir);
        }

        if (!success) {
            throw new GradleException("Test execution failed");
        }

        try {
            eclipseJob.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new GradleException("Test execution failed", e);
        }
    }

    private Directory cleanDirectory(String path) {
        Directory directory = getProject().getLayout().getBuildDirectory().dir(path).get();
        File f = directory.getAsFile();
        if (f.exists()) {
            try {
                MoreFiles.deleteRecursively(f.toPath(), RecursiveDeleteOption.ALLOW_INSECURE);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return getProject().getLayout().getBuildDirectory().dir(path).get();
    }

    private EclipseTestExtension getExtension(EclipseTestTask testTask) {
        return (EclipseTestExtension) testTask.getProject().getExtensions().findByName("eclipseTest");
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

    /**
     * Collects the names of the classes the PDE test runner should run, which are the top level,
     * concrete classes on the given classpath.
     */
    static List<String> collectTestNames(FileTree testClassFiles) {
        List<String> classNames = new ArrayList<String>();
        testClassFiles.visit(new EmptyFileVisitor() {

            @Override
            public void visitFile(FileVisitDetails fileDetails) {
                if (!isTestClassFile(fileDetails.getFile())) {
                    return;
                }
                classNames.add(fileDetails.getRelativePath().getPathString().replaceAll("\\.class", "").replace('/', '.'));
            }
        });
        LOGGER.info("collected test class names: {}", classNames);
        return classNames;
    }

    private static boolean isTestClassFile(File file) {
        if (!file.getAbsolutePath().endsWith(".class") || file.getAbsolutePath().contains("$")) {
            return false;
        }
        try {
            JavaClass javaClass = new ClassParser(file.getAbsolutePath()).parse();
            return !javaClass.isAbstract() && !javaClass.isInterface();
        } catch (Exception e) {
            LOGGER.warn("Cannot determine whether {} holds tests, skipping it", file, e);
            return false;
        }
    }
}
