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

import org.junit.*;
import org.slf4j.MDC;

import java.io.IOException;

public class IpLockTest {

    private static WorkerManager workerManager;

    @BeforeClass
    public static void setupClass() throws IOException, InterruptedException {
        MDC.put("IPL_ID", "0");

        workerManager = new WorkerManager();
        workerManager.start();
    }

    @AfterClass
    public static void tearDownClass() throws InterruptedException {
        workerManager.stop();
    }

    @Before
    public void setup() throws IOException {
    }

    @After
    public void tearDown() {
        workerManager.cleanup();
    }

    @Test
    public void testWorkerStepControlFirst() {
        ProcessHandle p = workerManager
            .builder()
            .activateBreakpoint(WorkerBreakpoint.BEFORE_LOCK)
            .start();

        p.waitForBreakpoint();

        p.proceedToBreakpoint(WorkerBreakpoint.AFTER_LOCK);

        p.proceed();
        p.waitFor();

        p.assertExitCode(WorkerExitCode.SUCCESS);
    }

    @Test
    public void testWorkerStepControlSecond() {
        ProcessHandle p = workerManager
            .builder()
            .activateBreakpoint(WorkerBreakpoint.BEFORE_LOCK)
            .start();

        p.waitForBreakpoint();

        p.proceedToBreakpoint(WorkerBreakpoint.AFTER_LOCK);

        p.proceed();
        p.waitFor();

        p.assertExitCode(WorkerExitCode.SUCCESS);
    }

    @Test
    public void testLockingSkippedFailure() {
        // kick off process that enters mutex area first
        workerManager
            .builder()
            .activateBreakpoint(WorkerBreakpoint.MUTEX_AREA)
            .startAndWaitForBreakpoint();

        ProcessHandle p = workerManager
            .builder()
            .useLock(false)
            .start();

        workerManager.await(p);

        workerManager.assertExitCode(WorkerExitCode.CONCURRENT_ACCESS_ERROR, p);
    }

    @Test
    public void testLockingBlocked() {
        // kick off process that enters mutex area first and blocks
        ProcessHandle blockingP = workerManager
            .builder()
            .activateBreakpoint(WorkerBreakpoint.MUTEX_AREA)
            .breakpointTimeoutMs(WorkerConstants.TIMEOUT_DISABLED)
            .startAndWaitForBreakpoint();

        ProcessHandle p1 = workerManager
            .builder()
            .workerLockTimeoutMs(10L)
            .start();

        workerManager.await(p1);

        workerManager.assertExitCode(WorkerExitCode.WORKER_LOCK_TIMEOUT, p1);
    }

    @Test
    public void testTryLockSuccess() throws IOException,
        InterruptedException {
        ProcessHandle p = workerManager
            .builder()
            .tryLock(true)
            .start();

        workerManager.await(p);
        workerManager.assertExitCode(WorkerExitCode.SUCCESS, p);
    }

    @Test
    public void testTryLockFailure() throws IOException,
        InterruptedException {
        // kick off blocking process
        workerManager
            .builder()
            .activateBreakpoint(WorkerBreakpoint.AFTER_LOCK)
            .startAndWaitForBreakpoint();

        ProcessHandle tryLockP = workerManager
            .builder()
            .tryLock(true)
            .start();

        workerManager.await(tryLockP);

        workerManager.assertExitCode(WorkerExitCode.TRY_LOCK_FAILED, tryLockP);
    }

    /*
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
*/
}
