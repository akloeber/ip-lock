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

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.NameFileFilter;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class ProcessHandle implements SignalHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessHandle.class);

    private static final long MAX_WAIT_TIMEOUT_MS = 5000;

    private static String javaExecutablePath;

    private static String javaClasspath;

    static {
        javaExecutablePath = determineJavaExecutablePath();
        javaClasspath = determineClasspath();
    }

    private final Phaser phaser;

    private Process process;

    private ProcessBuilder pb;

    private Integer id;

    private WorkerBreakpoint currentBreakpoint;

    private SignalDispatcher signalDispatcher;

    public ProcessHandle(WorkerBreakpoint breakpoint) {
        this.id = WorkerProcessId.next();
        this.currentBreakpoint = breakpoint;

        this.phaser = new Phaser(2) {

            @Override
            protected boolean onAdvance(int phase, int registeredParties) {
                LOGGER.info("process {} reached breakpoint {}", id, currentBreakpoint.name());
                currentBreakpoint = null;

                return false;
            }
        };

        this.pb = new ProcessBuilder(javaExecutablePath,
            "-classpath", javaClasspath, Worker.class.getName());
        this.pb.inheritIO();

        putEnv(WorkerEnv.ID, id);
    }

    private static String determineClasspath() {
        return System.getProperty("java.class.path");
    }

    private static String determineJavaExecutablePath() {
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

    public void putEnv(WorkerEnv var, Object val) {
        pb.environment().put(var.getVarName(), val.toString());
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

    public ProcessHandle kill() {
        process.destroy();
        return this;
    }

    public ProcessHandle start() {
        try {
            this.process = pb.start();
            return this;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
