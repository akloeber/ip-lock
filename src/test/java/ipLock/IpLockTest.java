/*
 * Copyright (c) 2015 Andreas Klöber
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package ipLock;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.NameFileFilter;
import org.apache.commons.lang.StringUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.MDC;

public class IpLockTest {

    private static final int SIGNAL_SERVER_PORT = 8080;

    private static File sharedResource;

    private static File syncFile;

    private static SignalServer signalServer;

    private int workerId;

    @BeforeClass
    public static void setupClass() throws IOException, InterruptedException {
        MDC.put("IPL_ID", "0");

        sharedResource = Paths.get(System.getProperty("java.io.tmpdir"),
                "ip-lock.shared").toFile();
        syncFile = Paths.get(System.getProperty("java.io.tmpdir"),
                "ip-lock.lock").toFile();

        sharedResource.deleteOnExit();
        syncFile.deleteOnExit();

        signalServer = new SignalServer();
        signalServer.start(SIGNAL_SERVER_PORT);
    }

    @AfterClass
    public static void tearDown() throws InterruptedException {
        signalServer.stop();
    }

    @Before
    public void setup() throws IOException {
        // make sure everything is clean before starting new test
        sharedResource.delete();
        syncFile.delete();

        workerId = 1;
    }

    @Test
    public void nop() throws InterruptedException, IOException {
        WorkerProcessBuilder workerBuilder = createWorkerBuilder(10);

        Process workerProcess = workerBuilder.start();

        waitForProcesses(workerProcess);

        assertExitCode(workerProcess, WorkerExitCode.SUCCESS);
    }

    @Test(expected = AssertionError.class)
    public void testLockingFailure() throws IOException,
            InterruptedException {
        List<WorkerProcessBuilder> workerBuilders = createWorkerBuilders(3);
        for (WorkerProcessBuilder workerBuilder : workerBuilders) {
            workerBuilder.useLock(false);
        }
        List<Process> processes = startWorkers(workerBuilders);

        waitForProcesses(processes);
        assertExitCode(processes, WorkerExitCode.SUCCESS);
    }

    @Test
    public void testLockingSuccess() throws IOException,
            InterruptedException {
        List<WorkerProcessBuilder> workerBuilders = createWorkerBuilders(3);
        List<Process> processes = startWorkers(workerBuilders);

        waitForProcesses(processes);
        assertExitCode(processes, WorkerExitCode.SUCCESS);
    }

    @Test
    public void testTryLockSuccess() throws IOException,
            InterruptedException {
        WorkerProcessBuilder tryLockWorkerBuilder = createWorkerBuilder(10);
        tryLockWorkerBuilder.tryLock(true);

        Process tryLockWorkerProcess = tryLockWorkerBuilder.start();
        waitForProcesses(tryLockWorkerProcess);

        assertExitCode(tryLockWorkerProcess, WorkerExitCode.SUCCESS);
    }

    @Test
    public void testTryLockFailure() throws IOException,
            InterruptedException {
        WorkerProcessBuilder blockingWorkerBuilder = createWorkerBuilder(100);
        WorkerProcessBuilder tryLockWorkerBuilder = createWorkerBuilder(10);
        tryLockWorkerBuilder.tryLock(true);

        Process blockingWorkerProcess = blockingWorkerBuilder.start();
        Thread.sleep(50);
        Process tryLockWorkerProcess = tryLockWorkerBuilder.start();
        waitForProcesses(blockingWorkerProcess, tryLockWorkerProcess);

        assertExitCode(blockingWorkerProcess, WorkerExitCode.SUCCESS);
        assertExitCode(tryLockWorkerProcess, WorkerExitCode.TRY_LOCK_FAILED);
    }

    @Test
    public void testWorkerStepControl() throws IOException,
            InterruptedException {
        WorkerProcessBuilder workerBuilder = createWorkerBuilder(100)
                .activateBreakpoints(WorkerBreakpoint.BEFORE_LOCK);

        Process workerProcess = workerBuilder.start();

        waitForProcesses(workerProcess);

        assertExitCode(workerProcess, WorkerExitCode.SUCCESS);
    }

    @Test
    public void testAutomaticUnlockWhenJvmStops() throws IOException,
            InterruptedException {
        WorkerProcessBuilder blockingWorkerBuilder = createWorkerBuilder(100);
        blockingWorkerBuilder.skipUnlock(true);
        WorkerProcessBuilder blockingLockWorker = createWorkerBuilder(10);

        Process blockingWorkerProcess = blockingWorkerBuilder.start();
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

    private List<WorkerProcessBuilder> createWorkerBuilders(int count) {
        List<WorkerProcessBuilder> result = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            result.add(createWorkerBuilder(10));
        }

        return result;
    }

    public WorkerProcessBuilder createWorkerBuilder(long workDurationMs) {
        WorkerProcessBuilder builder = WorkerProcessBuilder.builder(workerId++);
        builder
                .workDurationMs(workDurationMs)
                .signalServerPort(SIGNAL_SERVER_PORT)
                .resourcePath(sharedResource.getAbsolutePath())
                .syncFilePath(syncFile.getAbsolutePath())
                .useLock(true)
                .tryLock(false);

        return builder;
    }

    private List<Process> startWorkers(WorkerProcessBuilder... workers)
            throws IOException {
        return startWorkers(Arrays.asList(workers));
    }

    private List<Process> startWorkers(List<WorkerProcessBuilder> workers)
            throws IOException {
        List<Process> result = new ArrayList<>(workers.size());

        for (WorkerProcessBuilder worker : workers) {
            result.add(worker.start());
        }

        return result;
    }
}
