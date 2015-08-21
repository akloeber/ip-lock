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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The Class Worker.
 */
public class Worker implements SignalHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(Worker.class);

    private static final String MDC_IPL_ID = "IPL_ID";

    private Timer timeoutTimer;

    private Integer id;

    private SignalClient client;

    private CountDownLatch breakpointUnlockSignal;

    private Long breakpointTimeoutMs;

    private Long workerLockTimeoutMs;

    private File syncFile;

    private Boolean useLock;

    private Boolean tryLock;

    private Boolean skipUnlock;

    private Boolean haltInMutexArea;

    private WorkerBreakpoint breakpoint;

    private Integer serverPort;

    public Worker() {
        id = Integer.valueOf(extractEnv(WorkerEnv.ID));
        MDC.put(MDC_IPL_ID, id.toString());

        breakpointTimeoutMs = Long.parseLong(extractEnv(WorkerEnv.BREAKPOINT_TIMEOUT_MS));
        workerLockTimeoutMs = Long.parseLong(extractEnv(WorkerEnv.WORKER_LOCK_TIMEOUT_MS));
        /*
         * Shared resource that should be accessed exclusively. For testing a
		 * simple file is used.
		 */
        syncFile = new File(extractEnv(WorkerEnv.SYNC_FILE_PATH));
        useLock = Boolean.valueOf(extractEnv(WorkerEnv.USE_LOCK));
        tryLock = Boolean.valueOf(extractEnv(WorkerEnv.TRY_LOCK));
        skipUnlock = Boolean.valueOf(extractEnv(WorkerEnv.SKIP_UNLOCK));
        haltInMutexArea = Boolean.valueOf(extractEnv(WorkerEnv.HALT_IN_MUTEX_AREA));
        if (hasEnv(WorkerEnv.BREAKPOINT)) {
            activateBreakpoint(WorkerBreakpoint.valueOf(extractEnv(WorkerEnv.BREAKPOINT)));
        }
        serverPort = Integer.valueOf(extractEnv(WorkerEnv.SIGNAL_SERVER_PORT));

        // start timer for monitoring timeouts
        timeoutTimer = new Timer("timeoutTimer", true);
    }

    /**
     * The main method.
     *
     * @param args the arguments
     * @throws InterruptedException
     * @throws IOException
     */
    public static void main(String[] args) throws InterruptedException,
        IOException {

        Worker worker = new Worker();
        worker.connect();

        try {
            worker.run();
        } finally {
            worker.disconnect();
        }
    }

    private static String extractEnv(WorkerEnv var) {
        return System.getenv().get(var.getVarName());
    }

    private static boolean hasEnv(WorkerEnv var) {
        return System.getenv().containsKey(var.getVarName());
    }

    private static void exit(WorkerExitCode exitCode) {
        Runtime.getRuntime().exit(exitCode.getCode());
    }

    private static void halt(WorkerExitCode exitCode) {
        Runtime.getRuntime().halt(exitCode.getCode());
    }

    private void proceed() {
        if (breakpointUnlockSignal == null || breakpointUnlockSignal.getCount() == 0) {
            throw new AssertionError(String.format("process %d is not waiting at a breakpoint", id));
        }

        LOGGER.info("proceeding over breakpoint {}", breakpoint);
        breakpointUnlockSignal.countDown();
    }

    private void activateBreakpoint(WorkerBreakpoint breakpoint) {
        LOGGER.info("activating breakpoint {}", breakpoint);
        this.breakpoint = breakpoint;
    }

    private void breakpoint(WorkerBreakpoint currentBreakpoint) throws InterruptedException {
        if (breakpoint == currentBreakpoint) {
            LOGGER.info("stopped at breakpoint {}", currentBreakpoint);
            breakpointUnlockSignal = new CountDownLatch(1);

            client.dispatch(new Signal(id, SignalCode.BREAKPOINT, currentBreakpoint.toString()));

            if (breakpointTimeoutMs != WorkerConstants.TIMEOUT_DISABLED) {
                // timeout set
                boolean timeout = !breakpointUnlockSignal.await(breakpointTimeoutMs, TimeUnit.MILLISECONDS);
                if (timeout) {
                    LOGGER.info("stalled at breakpoint {}", currentBreakpoint);
                    exit(WorkerExitCode.BREAKPOINT_TIMEOUT);
                }
            } else {
                // timeout disabled
                breakpointUnlockSignal.await();
            }

            LOGGER.info("proceeded over breakpoint {}", currentBreakpoint);
        }
    }

    public void connect() throws InterruptedException {
        client = new SignalClient();
        client.connect(serverPort, this);
        client.dispatch(new Signal(id, SignalCode.CONNECT));
    }

    private void disconnect() throws InterruptedException {
        client.disconnect();
    }

    public void run() throws InterruptedException, IOException {
        IpLock ipLock = new IpLock(syncFile);

        LOGGER.info("starting worker");
        if (useLock) {
            breakpoint(WorkerBreakpoint.BEFORE_LOCK);

            // schedule task for lock timeout
            TimerTask workerLockTimeoutTask = new TimerTask() {

                @Override
                public void run() {
                    LOGGER.info("worker lock timeout");
                    exit(WorkerExitCode.WORKER_LOCK_TIMEOUT);
                }
            };
            timeoutTimer.schedule(workerLockTimeoutTask, workerLockTimeoutMs);

            if (tryLock) {
                // try lock
                LOGGER.info("acquiring lock (try)");
                boolean gotLock = ipLock.tryLock();
                if (!gotLock) {
                    LOGGER.error("failed to acquire lock");
                    exit(WorkerExitCode.TRY_LOCK_FAILED);
                }
            } else {
                // block lock
                LOGGER.info("acquiring lock (block)");
                ipLock.lock();
            }

            // unschedule task for lock timeout
            workerLockTimeoutTask.cancel();

            LOGGER.info("acquired lock");
            breakpoint(WorkerBreakpoint.AFTER_LOCK);
        }

        try {

            LOGGER.info("entered mutex area");

            if (haltInMutexArea) {
                LOGGER.info("doing halt in mutex area");
                halt(WorkerExitCode.HALT_IN_MUTEX_AREA);
            }

            breakpoint(WorkerBreakpoint.MUTEX_AREA);

            LOGGER.info("leaving mutex area");

        } finally {
            if (useLock && !skipUnlock) {
                LOGGER.info("releasing lock");
                ipLock.unlock();
                breakpoint(WorkerBreakpoint.AFTER_UNLOCK);
            }
            LOGGER.info("finished");
        }
    }

    @Override
    public void handleSignal(Signal sig) {
        switch (sig.getCode()) {
            case PROCEED:
                proceed();
                break;
            case BREAKPOINT:
                activateBreakpoint(WorkerBreakpoint.valueOf(sig.getParams()[0]));
                break;
        }
    }
}
