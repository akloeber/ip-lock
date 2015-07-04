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

    private WorkerBreakpoint waitingBreakpoint;

    private WorkerBreakpoint targetBreakpoint;

    public ProcessHandle(Integer id, Process process) {
        this.id = id;
        this.process = process;
    }

    public void waitForBreakpoint(WorkerBreakpoint breakpoint) throws InterruptedException {
        synchronized (this) {
            if (this.waitingBreakpoint != null) {
                // the process is waiting at a breakpoint

                if (this.waitingBreakpoint == breakpoint) {
                    LOGGER.info("process {} has already reached breakpoint {} before", id, breakpoint);
                    return;
                } else {
                    throw new AssertionError(String.format("process %d is already waiting at breakpoint %s", id, breakpoint.toString()));
                }
            }

            this.targetBreakpoint = breakpoint;
            this.waitingSignal = new CountDownLatch(1);
        }

        LOGGER.info("waiting for process {} to reach breakpoint {}", id, breakpoint);
        boolean timeout = !waitingSignal.await(MAX_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        if (timeout) {
            fail(String.format("Timeout while waiting for process to reach breakpoint %s", breakpoint.toString()));
        } else {
            LOGGER.info("process {} reached breakpoint {}", id, breakpoint);
        }
    }

    public void signalBreakpoint(WorkerBreakpoint breakpoint) {
        synchronized (this) {
            if (this.targetBreakpoint != null) {
                // there is a target breakpoint
                if (this.targetBreakpoint != breakpoint) {
                    // this is not the expected target endpoint
                    throw new AssertionError(String.format("waiting for breakpoint %s but process %d stopped at breakpoint %s",
                        this.targetBreakpoint.toString(), id, breakpoint.toString()));
                } else {
                    // process is waiting at target breakpoint
                    this.waitingBreakpoint = breakpoint;
                    this.waitingSignal.countDown();
                }
            } else {
                // not waiting for a target breakpoint, so ignore for now
                this.waitingBreakpoint = breakpoint;
            }
        }
    }

    public void proceedToBreakpoint(WorkerBreakpoint breakpoint) {
        // send command
        this.waitingBreakpoint = null;
    }

    public void proceed() {
        // send command
        this.waitingBreakpoint = null;
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
