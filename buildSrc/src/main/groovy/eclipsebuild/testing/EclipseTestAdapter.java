package eclipsebuild.testing;

import eclipsebuild.testing.EclipseTestEvent.*;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.testing.GroupTestEventReporter;
import org.gradle.api.tasks.testing.TestEventReporter;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class EclipseTestAdapter {

    private static final Logger LOGGER = Logging.getLogger(EclipseTestAdapter.class);
    private final BlockingQueue<EclipseTestEvent> queue;
    private final GroupTestEventReporter testEventReporter;

    public EclipseTestAdapter(EclipseTestListener testListener, GroupTestEventReporter testEventReporter) {
        this.queue = testListener.getQueue();
        this.testEventReporter = testEventReporter;
    }

    public boolean processEvents() {
        boolean success = true;
        Map<String, GroupTestEventReporter> runningTestClasses = new HashMap<>();
        Map<String, Set<String>> testIdsInTestClass = new HashMap<>();
        Map<String, Instant> lastTimeWhenTestMethodFinishedInClass = new HashMap<>();
        Set<String> failedTestClasses = new HashSet<>();
        Map<String, TestEventReporter> runningTestMethods = new TreeMap<>();
        Set<String> failedTests = new HashSet<>();

        testEventReporter.started(Instant.now());

        while (true) {
            try {
                EclipseTestEvent event = queue.poll(5, TimeUnit.MINUTES);
                LOGGER.info("Received event: {}", event);
                if (event instanceof TestTreeEntry) {
                    TestTreeEntry testTreeEntry = (TestTreeEntry) event;
                    Set<String> testIds = testIdsInTestClass.getOrDefault(testTreeEntry.getClassName(), new HashSet<>());
                    testIds.add(testTreeEntry.getTestId());
                    testIdsInTestClass.put(testTreeEntry.getClassName(), testIds);
                } else if (event instanceof TestStarted) {
                    TestStarted testStarted = (TestStarted) event;
                    GroupTestEventReporter testClass = runningTestClasses.get(testStarted.getClassName());
                    if (testClass == null) {
                        testClass = testEventReporter.reportTestGroup(testStarted.getClassName());
                        testClass.started(testStarted.getWhen());
                        runningTestClasses.put(testStarted.getClassName(), testClass);
                    }
                    // the method name is used for file names and should not contain special characters
                    String normalizedMethodName = normalize(testStarted.getMethodName());
                    TestEventReporter test = testClass.reportTest(normalizedMethodName, normalizedMethodName);
                    test.started(testStarted.getWhen());
                    runningTestMethods.put(testStarted.getTestId(), test);
                } else if (event instanceof TestEnded) {
                    TestEnded testEnded = (TestEnded) event;
                    // test failure result in two events: TestFailed and TestEnded
                    if (!failedTests.contains(testEnded.getTestId())) {
                        TestEventReporter test = runningTestMethods.remove(testEnded.getTestId());
                        if (testEnded.getMethodName().startsWith("@Ignore: ")) {
                            test.skipped(testEnded.getWhen());
                        } else {
                            test.succeeded(testEnded.getWhen());
                        }
                        lastTimeWhenTestMethodFinishedInClass.put(testEnded.getClassName(), testEnded.getWhen());
                        test.close();
                    }
                    // update how many methods we still expect to run in the method
                    Set<String> testIds = testIdsInTestClass.get(testEnded.getClassName());
                    testIds.remove(testEnded.getTestId());
                    testIdsInTestClass.put(testEnded.getClassName(), testIds);
                    // finish the test class if all methods have been run
                    if (testIds.size() <= 1) {
                        GroupTestEventReporter testClass = runningTestClasses.remove(testEnded.getClassName());
                        boolean failed = failedTestClasses.remove(testEnded.getClassName());
                        if (failed) {
                            testClass.failed(testEnded.getWhen());
                        } else {
                            testClass.succeeded(testEnded.getWhen());
                        }
                        testClass.close();
                    }
                } else if (event instanceof TestFailed) {
                    success = false;
                    TestFailed testFailed = (TestFailed) event;
                    TestEventReporter test = runningTestMethods.remove(testFailed.getTestId());
                    // the Gradle API has a bug: it replaces the trace (last arg) with the stacktrace of the call site
                    // to work around this, we send the stacktrace as the message parameter too
                    test.failed(testFailed.getWhen(), testFailed.getTrace(), testFailed.getTrace());
                    lastTimeWhenTestMethodFinishedInClass.put(testFailed.getClassName(), testFailed.getWhen());
                    failedTestClasses.add(testFailed.getClassName());
                    failedTests.add(testFailed.getTestId());
                    test.close();
                }

                if (event == null || event instanceof EclipseTestEvent.TestRunEnded) {
                    for (Map.Entry<String, GroupTestEventReporter> runningTest : runningTestClasses.entrySet()) {
                        String className = runningTest.getKey();
                        Instant when = lastTimeWhenTestMethodFinishedInClass.get(className);
                        GroupTestEventReporter testClass = runningTest.getValue();
                        boolean failed = failedTestClasses.remove(className);
                        if (failed) {
                            testClass.failed(when);
                        } else {
                            testClass.succeeded(when);
                        }
                        testClass.close();
                    }
                    break;
                }
            } catch (InterruptedException e) {
                // retry
            }
        }
        if (success) {
            testEventReporter.succeeded(Instant.now());
            testEventReporter.close();
        } else {
            testEventReporter.failed(Instant.now(), "Some tests failed!");
            testEventReporter.close();
        }

         return success;
    }

    private String normalize(String input) {
        return input.replaceAll("[\\\\/:*?\"<>|/\0]", "-");
    }
}
