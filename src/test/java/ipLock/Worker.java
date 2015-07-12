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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The Class Worker.
 */
public class Worker implements SignalHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(Worker.class);

    private static final long MAX_WAIT_TIMEOUT_MS = 5000;

    private static final String MDC_IPL_ID = "IPL_ID";

    private Integer id;

    private SignalClient client;

    private CountDownLatch breakpointUnlockSignal;

    private Integer workDurationMs;

    private File resource;

    private File syncFile;

    private Boolean useLock;

    private Boolean tryLock;

    private Boolean skipUnlock;

    private WorkerBreakpoint breakpoint;

    private Integer serverPort;

    public Worker() {
        id = Integer.valueOf(extractEnv(WorkerEnv.ID));
        MDC.put(MDC_IPL_ID, id.toString());

        workDurationMs = Integer.parseInt(extractEnv(WorkerEnv.WORK_DURATION_MS));
        /*
         * Shared resource that should be accessed exclusively. For testing a
		 * simple file is used.
		 */
        resource = new File(extractEnv(WorkerEnv.SHARED_RESOURCE_PATH));
        syncFile = new File(extractEnv(WorkerEnv.SYNC_FILE_PATH));
        useLock = Boolean.valueOf(extractEnv(WorkerEnv.USE_LOCK));
        tryLock = Boolean.valueOf(extractEnv(WorkerEnv.TRY_LOCK));
        skipUnlock = Boolean.valueOf(extractEnv(WorkerEnv.SKIP_UNLOCK));
        if (hasEnv(WorkerEnv.BREAKPOINT)) {
            breakpoint = WorkerBreakpoint.valueOf(extractEnv(WorkerEnv.BREAKPOINT));
            LOGGER.info("will stop at breakpoint {}", breakpoint);
        }
        serverPort = Integer.valueOf(extractEnv(WorkerEnv.SIGNAL_SERVER_PORT));
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
        System.exit(exitCode.getCode());
    }

    private void proceed() {
        if (breakpointUnlockSignal.getCount() == 0) {
            throw new AssertionError(String.format("process %d is not waiting at a breakpoint", id));
        }

        breakpointUnlockSignal.countDown();
    }

    private void breakpoint(WorkerBreakpoint currentBreakpoint) throws InterruptedException {
        if (breakpoint == currentBreakpoint) {
            LOGGER.info("stopped at breakpoint {}", currentBreakpoint);
            breakpointUnlockSignal = new CountDownLatch(1);

            client.send(new Signal(id, SignalCode.BREAKPOINT, currentBreakpoint.toString()));
            boolean timeout = !breakpointUnlockSignal.await(MAX_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            if (timeout) {
                LOGGER.info("stalled at breakpoint {}", currentBreakpoint);
                exit(WorkerExitCode.BREAKPOINT_TIMEOUT);
            } else {
                LOGGER.info("proceeded over breakpoint {}", currentBreakpoint);
            }
        }
    }

    public void connect() throws InterruptedException {
        client = new SignalClient();
        client.connect(serverPort, this);
        client.send(new Signal(id, SignalCode.CONNECT));
    }

    private void disconnect() throws InterruptedException {
        client.disconnect();
    }

    public void run() throws InterruptedException, IOException {
        IpLock ipLock = new IpLock(syncFile);

        LOGGER.info("starting worker");
        if (useLock) {
            breakpoint(WorkerBreakpoint.BEFORE_LOCK);
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
            LOGGER.info("acquired lock");
            breakpoint(WorkerBreakpoint.AFTER_LOCK);
        }

        try {
            breakpoint(WorkerBreakpoint.MUTEX_AREA);

            if (resource.exists()) {
                LOGGER.error("shared resource already exists; maybe created by concurrent process?");
                exit(WorkerExitCode.CONCURRENT_ACCESS_ERROR);
            }

            LOGGER.info("creating shared resource");
            boolean createResult = resource.createNewFile();
            if (!createResult) {
                LOGGER.error("could not create file; maybe created by concurrent process?");
                exit(WorkerExitCode.CONCURRENT_ACCESS_ERROR);
            }

            Thread.sleep(workDurationMs);

            LOGGER.info("deleting shared resource");
            boolean deleteResult = resource.delete();
            if (!deleteResult) {
                LOGGER.error("could not delete file; maybe deleted by concurrent process?");
                exit(WorkerExitCode.CONCURRENT_ACCESS_ERROR);
            }
        } finally {
            if (useLock && !skipUnlock) {
                LOGGER.info("releasing lock");
                ipLock.unlock();
                breakpoint(WorkerBreakpoint.AFTER_UNLOCK);
            }
        }
    }

    @Override
    public void handleSignal(Signal sig) {
        switch (sig.getCode()) {
            case PROCEED:
                proceed();
                break;
        }
    }
}
