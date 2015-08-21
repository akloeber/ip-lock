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
    public void testWorkerStepControl() {
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
    public void testMultipleProcessesInMutexAreaIfNoLockUsed() {
        // kick off process that enters mutex area first and stays
        workerManager
            .builder()
            .activateBreakpoint(WorkerBreakpoint.MUTEX_AREA)
            .startAndWaitForBreakpoint();

        // kick off process that does not use locking and thus proceeds through mutex area
        ProcessHandle p = workerManager
            .builder()
            .useLock(false)
            .activateBreakpoint(WorkerBreakpoint.MUTEX_AREA)
            .startAndWaitForBreakpoint()
            .proceed();

        workerManager.await(p);

        workerManager.assertExitCode(WorkerExitCode.SUCCESS, p);
    }

    @Test
    public void testLockingBlocked() {
        // kick off process that enters mutex area first and blocks
        workerManager
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

    @Test
    public void testAutomaticUnlockWhenProcessFinishesRegularly() throws IOException,
        InterruptedException {
        ProcessHandle blockingP = workerManager
            .builder()
            .activateBreakpoint(WorkerBreakpoint.MUTEX_AREA)
            .skipUnlock(true)
            .startAndWaitForBreakpoint();

        ProcessHandle blockedP = workerManager
            .builder()
            .activateBreakpoint(WorkerBreakpoint.BEFORE_LOCK)
            .startAndWaitForBreakpoint();

        blockedP.proceed();
        blockingP.proceed();

        workerManager.await(blockingP, blockedP);
        workerManager.assertExitCode(WorkerExitCode.SUCCESS, blockingP, blockedP);
    }

    @Test
    public void testAutomaticUnlockWhenProcessIsDestroyed() throws IOException,
        InterruptedException {
        ProcessHandle blockingP = workerManager
            .builder()
            .activateBreakpoint(WorkerBreakpoint.MUTEX_AREA)
            .startAndWaitForBreakpoint();

        ProcessHandle blockedP = workerManager
            .builder()
            .activateBreakpoint(WorkerBreakpoint.BEFORE_LOCK)
            .startAndWaitForBreakpoint();

        blockedP.proceed();
        blockingP.destroy();

        workerManager.await(blockingP, blockedP);
        workerManager.assertExitCode(WorkerExitCode.SUCCESS, blockedP);
    }

    @Test
    public void testAutomaticUnlockWhenProcessHaltsItself() throws IOException,
        InterruptedException {
        ProcessHandle haltingP = workerManager
            .builder()
            .haltInMutexArea(true)
            .startAndWait();

        ProcessHandle normalP = workerManager
            .builder()
            .startAndWait();

        workerManager.assertExitCode(WorkerExitCode.HALT_IN_MUTEX_AREA, haltingP);
        workerManager.assertExitCode(WorkerExitCode.SUCCESS, normalP);
    }

}
