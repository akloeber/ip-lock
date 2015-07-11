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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ProcessHandle {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessHandle.class);

    private static final long MAX_WAIT_TIMEOUT_MS = 5000;

    private Process process;

    private Integer id;

    private CountDownLatch waitingSignal;

    private WorkerBreakpoint waitingAtBreakpoint;

    public ProcessHandle(Integer id, Process process) {
        this.id = id;
        this.process = process;
    }

    public void signalBreakpoint(WorkerBreakpoint breakpoint) {
        synchronized (this) {
            if (waitingAtBreakpoint != null) {
                throw new IllegalStateException(String.format("process was already waiting at breakpoint %s " +
                    "when signal for breakpoint %s arrived ", waitingAtBreakpoint.name(), breakpoint.name()));
            }

            if (this.waitingSignal != null) {
                // the manager has been waiting for this breakpoint to be reached
                this.waitingSignal.countDown();
            }

            this.waitingAtBreakpoint = breakpoint;
        }
    }

    public void proceedToBreakpoint(WorkerBreakpoint breakpoint) throws InterruptedException {
        activateBreakpoint(breakpoint);
        proceed();
        waitForBreakpoint();
    }

    public void activateBreakpoint(WorkerBreakpoint breakpoint) {
        // TODO: send BREAKPOINT signal
    }

    public void proceed() {
        this.waitingAtBreakpoint = null;
        // TODO: send PROCEED signal
    }

    public void waitForBreakpoint() throws InterruptedException {
        synchronized (this) {
            if (waitingAtBreakpoint != null) {
                // the process is already waiting at the breakpoint
                LOGGER.info("process {} has already reached breakpoint {} before", id, waitingAtBreakpoint);
                return;
            }

            this.waitingSignal = new CountDownLatch(1);
        }

        LOGGER.info("waiting for process {} to reach breakpoint", id);
        boolean timeout = !waitingSignal.await(MAX_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        if (timeout) {
            fail("Timeout while waiting for process to reach breakpoint");
        } else {
            LOGGER.info("process {} reached breakpoint", id);
        }
    }

    public Process getProcess() {
        return process;
    }

    public Integer getId() {
        return id;
    }

    public void assertExitCode(WorkerExitCode exitCode) {
        assertEquals(exitCode.getCode(), getProcess().exitValue());
    }
}
