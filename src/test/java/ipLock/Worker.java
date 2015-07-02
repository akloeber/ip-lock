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

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The Class Worker.
 */
public class Worker {

    private static final Logger LOGGER = LoggerFactory.getLogger(Worker.class);

    private static final long MAX_WAIT_TIMEOUT_MS = 5000;

    private static Set<WorkerBreakpoint> breakpoints = new HashSet<>();

    private static CountDownLatch breakpointUnlockSignal;

    private static SignalClient client;

    /**
     * The main method.
     *
     * @param args the arguments
     * @throws InterruptedException
     * @throws IOException
     */
    public static void main(String[] args) throws InterruptedException,
            IOException {
        MDC.put("IPL_ID", System.getenv().get("IPL_ID"));

        int workDurationMs = Integer.parseInt(System.getenv().get("IPL_WORK_DURATION_MS"));
        /*
         * Shared resource that should be accessed exclusively. For testing a
		 * simple file is used.
		 */
        File resource = new File(System.getenv().get("IPL_RESOURCE_PATH"));
        File syncFile = new File(System.getenv().get("IPL_SYNC_FILE_PATH"));
        boolean useLock = Boolean.valueOf(System.getenv().get("IPL_USE_LOCK"));
        boolean tryLock = Boolean.valueOf(System.getenv().get("IPL_TRY_LOCK"));
        boolean skipUnlock = Boolean.valueOf(System.getenv().get("IPL_SKIP_UNLOCK"));
        if (System.getenv().containsKey("IPL_BREAKPOINTS")) {
            String[] breakpointNames = StringUtils.split(System.getenv().get("IPL_BREAKPOINTS"), ':');
            for (String name : breakpointNames) {
                WorkerBreakpoint breakpoint = WorkerBreakpoint.valueOf(name);
                breakpoints.add(breakpoint);
            }
            LOGGER.info("will stop at breakpoints {}", ArrayUtils.toString(breakpointNames));
        }
        int port = Integer.valueOf(System.getenv().get("IPL_SIGNAL_SERVER_PORT"));

        client = new SignalClient();
        client.connect(port);
        client.send(new Signal(SignalCode.CONNECT));

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

            client.disconnect();
        }
    }

    private static void breakpoint(WorkerBreakpoint currentBreakpoint) throws InterruptedException {
        if (breakpoints.contains(currentBreakpoint)) {
            LOGGER.info("stopped at breakpoint {}", currentBreakpoint);
            breakpointUnlockSignal = new CountDownLatch(1);

            client.send(new Signal(SignalCode.BREAKPOINT, currentBreakpoint.toString()));
            boolean timeout = !breakpointUnlockSignal.await(MAX_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            if (timeout) {
                LOGGER.info("stalled at breakpoint {}", currentBreakpoint);
                exit(WorkerExitCode.BREAKPOINT_TIMEOUT);
            } else {
                LOGGER.info("breakpoint {} unlocked", currentBreakpoint);
            }
        }
    }

    private static void exit(WorkerExitCode exitCode) {
        System.exit(exitCode.getCode());
    }
}
