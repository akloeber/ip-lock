package ipLock;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.NameFileFilter;
import org.apache.commons.lang.StringUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class IpLockTest {

    private static String javaExecutablePath;
    private static String javaClasspath;
    private static File sharedResource;
    private static File syncFile;

    @BeforeClass
    public static void setupClass() throws IOException {
        javaExecutablePath = determineJavaExecutablePath();
        javaClasspath = determineClasspath();
        sharedResource = Paths.get(System.getProperty("java.io.tmpdir"),
                "ip-lock.shared").toFile();
        syncFile = Paths.get(System.getProperty("java.io.tmpdir"),
                "ip-lock.lock").toFile();

        sharedResource.deleteOnExit();
        syncFile.deleteOnExit();
    }

    @Before
    public void setup() throws IOException {
        // make sure everything is clean before starting new test
        sharedResource.delete();
        syncFile.delete();
    }

    @Test(expected = AssertionError.class)
    public void testLockingFailure() throws IOException,
            InterruptedException {
        List<ProcessBuilder> workers = createWorkers(3);
        for (ProcessBuilder worker : workers) {
            worker.environment().put("IPL_USE_LOCK", Boolean.FALSE.toString());
        }
        List<Process> processes = startWorkers(workers);

        waitForProcesses(processes);
        assertExitCode(processes, WorkerExitCode.SUCCESS);
    }

    @Test
    public void testLockingSuccess() throws IOException,
            InterruptedException {
        List<ProcessBuilder> workers = createWorkers(3);
        List<Process> processes = startWorkers(workers);

        waitForProcesses(processes);
        assertExitCode(processes, WorkerExitCode.SUCCESS);
    }

    @Test
    public void testTryLockSuccess() throws IOException,
            InterruptedException {
        ProcessBuilder tryLockWorker = createWorker(1, 10);
        tryLockWorker.environment().put("IPL_TRY_LOCK", Boolean.TRUE.toString());

        Process tryLockWorkerProcess = tryLockWorker.start();
        waitForProcesses(tryLockWorkerProcess);

        assertExitCode(tryLockWorkerProcess, WorkerExitCode.SUCCESS);
    }

    @Test
    public void testTryLockFailure() throws IOException,
            InterruptedException {
        ProcessBuilder blockingWorker = createWorker(0, 100);
        ProcessBuilder tryLockWorker = createWorker(1, 10);
        tryLockWorker.environment().put("IPL_TRY_LOCK", Boolean.TRUE.toString());

        Process blockingWorkerProcess = blockingWorker.start();
        Thread.sleep(50);
        Process tryLockWorkerProcess = tryLockWorker.start();
        waitForProcesses(blockingWorkerProcess, tryLockWorkerProcess);

        assertExitCode(blockingWorkerProcess, WorkerExitCode.SUCCESS);
        assertExitCode(tryLockWorkerProcess, WorkerExitCode.TRY_LOCK_FAILED);
    }

    @Test
    public void testAutomaticUnlockWhenJvmStops() throws IOException,
            InterruptedException {
        ProcessBuilder blockingWorker = createWorker(0, 100);
        blockingWorker.environment().put("IPL_SKIP_UNLOCK", Boolean.TRUE.toString());
        ProcessBuilder blockingLockWorker = createWorker(1, 10);

        Process blockingWorkerProcess = blockingWorker.start();
        Thread.sleep(50);
        Process tryLockWorkerProcess = blockingLockWorker.start();
        waitForProcesses(blockingWorkerProcess, tryLockWorkerProcess);

        assertExitCode(blockingWorkerProcess, WorkerExitCode.SUCCESS);
        assertExitCode(tryLockWorkerProcess, WorkerExitCode.SUCCESS);
    }

    private void waitForProcesses(Process... processes)
            throws InterruptedException {
        waitForProcesses(Arrays.asList(processes));
    }

    private void waitForProcesses(List<Process> processes)
            throws InterruptedException {
        for (Process process : processes) {
            process.waitFor();
        }
    }

    private void assertExitCode(List<Process> workerProcesses, WorkerExitCode exitCode) {
        for (Process workerProcess : workerProcesses) {
            assertExitCode(workerProcess, exitCode);
        }
    }

    private void assertExitCode(Process workerProcess, WorkerExitCode exitCode) {
        assertEquals(exitCode.getCode(), workerProcess.exitValue());
    }

    private List<ProcessBuilder> createWorkers(int count) {
        List<ProcessBuilder> result = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            result.add(createWorker(i, 10));
        }

        return result;
    }

    private List<Process> startWorkers(List<ProcessBuilder> workers)
            throws IOException {
        List<Process> result = new ArrayList<>(workers.size());

        for (ProcessBuilder pb : workers) {
            result.add(pb.start());
        }

        return result;
    }

    private List<Process> startWorkers(ProcessBuilder... workers)
            throws IOException {
        return startWorkers(Arrays.asList(workers));
    }

    private ProcessBuilder createWorker(int id, int workDurationMs) {
        ProcessBuilder pb = new ProcessBuilder(javaExecutablePath,
                "-classpath", javaClasspath, Worker.class.getName());
        pb.inheritIO();
        pb.environment().put("IPL_ID", Integer.toString(id));
        pb.environment().put("IPL_WORK_DURATION_MS", Integer.toString(workDurationMs));
        pb.environment().put("IPL_RESOURCE_PATH", sharedResource.getAbsolutePath());
        pb.environment().put("IPL_SYNC_FILE_PATH", syncFile.getAbsolutePath());
        pb.environment().put("IPL_USE_LOCK", Boolean.TRUE.toString());
        pb.environment().put("IPL_TRY_LOCK", Boolean.FALSE.toString());

        return pb;
    }

    private static String determineJavaExecutablePath() throws IOException {
        File javaHome = new File(System.getProperty("java.home"));

        Collection<File> files = FileUtils.listFiles(javaHome,
                new NameFileFilter("java"), new NameFileFilter("bin"));

        if (files.isEmpty()) {
            throw new RuntimeException(
                    "No java executable found at java home '" + javaHome + "'");
        }
        if (files.size() > 1) {
            throw new RuntimeException(
                    "Multiple java executables found at java home '" + javaHome
                            + "': " + StringUtils.join(files, "; "));
        }

        return Collections.min(files).getAbsolutePath();
    }

    private static String determineClasspath() {
        return System.getProperty("java.class.path");
    }
}
