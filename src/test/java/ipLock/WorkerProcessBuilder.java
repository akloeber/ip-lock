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

import java.io.File;
import java.nio.file.Paths;

public abstract class WorkerProcessBuilder {

    private static final Integer DEFAULT_SIGNAL_SERVER_PORT = 8080;

    private static final long DEFAULT_BREAKPOINT_TIMEOUT_MS = 5000;

    private static final long DEFAULT_WORKER_LOCK_TIMEOUT_MS = 5000;

    private static String tempDirPath;

    static {
        tempDirPath = determineTempDirPath();
    }

    private Integer signalServerPort;

    private Long breakpointTimeoutMs;

    private Object workerLockTimeoutMs;

    private Boolean useLock;

    private Boolean tryLock;

    private Boolean skipUnlock;

    private Boolean haltInMutexArea;

    private File syncFile;

    private WorkerBreakpoint breakpoint;

    public WorkerProcessBuilder() {
        this.syncFile = Paths.get(tempDirPath, "ip-lock.lock").toFile();
        this.signalServerPort = DEFAULT_SIGNAL_SERVER_PORT;
        this.useLock = Boolean.TRUE;
        this.tryLock = Boolean.FALSE;
        this.skipUnlock = Boolean.FALSE;
        this.haltInMutexArea = Boolean.FALSE;
        this.breakpointTimeoutMs = DEFAULT_BREAKPOINT_TIMEOUT_MS;
        this.workerLockTimeoutMs = DEFAULT_WORKER_LOCK_TIMEOUT_MS;
    }

    private static String determineTempDirPath() {
        return System.getProperty("java.io.tmpdir");
    }

    public boolean hasActiveBreakpoint() {
        return this.breakpoint != null;
    }

    public WorkerProcessBuilder signalServerPort(Integer port) {
        this.signalServerPort = port;
        return this;
    }

    public WorkerProcessBuilder tryLock(Boolean tryLock) {
        this.tryLock = tryLock;
        return this;
    }

    public WorkerProcessBuilder useLock(Boolean useLock) {
        this.useLock = useLock;
        return this;
    }

    public WorkerProcessBuilder skipUnlock(Boolean skipUnlock) {
        this.skipUnlock = skipUnlock;
        return this;
    }

    public WorkerProcessBuilder haltInMutexArea(Boolean haltInMutexArea) {
        this.haltInMutexArea = haltInMutexArea;
        return this;
    }

    public WorkerProcessBuilder syncFile(File syncFile) {
        this.syncFile = syncFile;
        return this;
    }

    public WorkerProcessBuilder activateBreakpoint(WorkerBreakpoint breakpoints) {
        this.breakpoint = breakpoints;
        return this;
    }

    public WorkerProcessBuilder breakpointTimeoutMs(Long breakpointTimeoutMs) {
        this.breakpointTimeoutMs = breakpointTimeoutMs;
        return this;
    }

    public WorkerProcessBuilder workerLockTimeoutMs(long workerLockTimeoutMs) {
        this.workerLockTimeoutMs = workerLockTimeoutMs;
        return this;
    }

    private ProcessHandle build() {

        ProcessHandle ph = new ProcessHandle(breakpoint);

        ph.putEnv(WorkerEnv.SIGNAL_SERVER_PORT, signalServerPort);
        ph.putEnv(WorkerEnv.TRY_LOCK, tryLock);
        ph.putEnv(WorkerEnv.USE_LOCK, useLock);
        ph.putEnv(WorkerEnv.SKIP_UNLOCK, skipUnlock);
        ph.putEnv(WorkerEnv.HALT_IN_MUTEX_AREA, haltInMutexArea);
        ph.putEnv(WorkerEnv.BREAKPOINT_TIMEOUT_MS, breakpointTimeoutMs);
        ph.putEnv(WorkerEnv.WORKER_LOCK_TIMEOUT_MS, workerLockTimeoutMs);
        ph.putEnv(WorkerEnv.SYNC_FILE_PATH, syncFile.getAbsolutePath());
        if (breakpoint != null) {
            ph.putEnv(WorkerEnv.BREAKPOINT, breakpoint);
        }

        return ph;
    }

    public ProcessHandle start() {
        return start(1)[0];
    }

    public ProcessHandle startAndWaitForBreakpoint() {
        return start().waitForBreakpoint();
    }


    public ProcessHandle[] start(int count) {
        ProcessHandle[] pha = new ProcessHandle[count];

        for (int i = 0; i < count; i++) {
            ProcessHandle ph = build();

            onProcessCreated(ph);

            pha[i] = ph.start();
        }

        return pha;
    }

    public ProcessHandle startAndWait() {
        return start().waitFor();
    }

    protected abstract void onProcessCreated(ProcessHandle process);
}
