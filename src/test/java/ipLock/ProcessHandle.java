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

import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class ProcessHandle implements SignalHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessHandle.class);

    private static final long MAX_WAIT_TIMEOUT_MS = 5000;

    private final Phaser phaser;

    private Process process;

    private Integer id;

    private WorkerBreakpoint currentBreakpoint;

    private SignalDispatcher signalDispatcher;

    public ProcessHandle(final Integer id, final Process process, WorkerBreakpoint breakpoint) {
        this.id = id;
        this.process = process;
        this.currentBreakpoint = breakpoint;

        this.phaser = new Phaser(2) {

            @Override
            protected boolean onAdvance(int phase, int registeredParties) {
                LOGGER.info("process {} reached breakpoint {}", id, currentBreakpoint.name());
                currentBreakpoint = null;

                return false;
            }
        };
    }

    @Override
    public void handleSignal(Signal sig) {
        // invoked on HANDLER
        switch (sig.getCode()) {
            case CONNECT:
                break;
            case BREAKPOINT:
                arriveAndAwait();
                break;
        }
    }

    public void waitForBreakpoint() {
        // invoked on MAIN
        arriveAndAwait();
    }

    private void arriveAndAwait() {
        try {
            LOGGER.info("arrive and await on phaser");
            phaser.awaitAdvanceInterruptibly(phaser.arrive(), MAX_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void proceedToBreakpoint(WorkerBreakpoint breakpoint) {
        // invoked on MAIN
        activateBreakpoint(breakpoint);
        proceed();
        waitForBreakpoint();
    }

    public void activateBreakpoint(WorkerBreakpoint breakpoint) {
        // invoked on MAIN
        if (this.currentBreakpoint != null) {
            throw new IllegalStateException(String.format("breakpoint %s " +
                "is already active", breakpoint.name()));
        }
        this.currentBreakpoint = breakpoint;
        signalDispatcher.dispatch(new Signal(0, SignalCode.BREAKPOINT, breakpoint.name()));
    }

    public void proceed() {
        // invoked on MAIN
        signalDispatcher.dispatch(new Signal(0, SignalCode.PROCEED));
    }

    public void waitFor() {
        try {
            process.waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
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

    public void setSignalDispatcher(SignalDispatcher dispatcher) {
        this.signalDispatcher = dispatcher;
    }

    public void kill() {
        process.destroy();
    }
}
