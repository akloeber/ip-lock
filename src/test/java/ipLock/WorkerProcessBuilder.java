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

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;

public class WorkerProcessBuilder {

    private static final Integer DEFAULT_SIGNAL_SERVER_PORT = 8080;

    private static final Long DEFAULT_WORK_DURATION_MS = 10L;

    private static String javaExecutablePath;

    private static String javaClasspath;

    private static String tempDirPath;

    static {
        javaExecutablePath = determineJavaExecutablePath();
        javaClasspath = determineClasspath();
        tempDirPath = determineTempDirPath();
    }

    private Integer id;

    private Integer signalServerPort;

    private Boolean useLock;

    private Boolean tryLock;

    private Boolean skipUnlock;

    private Long workDurationMs;

    private File sharedResource;

    private File syncFile;

    private WorkerBreakpoint breakpoint;

    public WorkerProcessBuilder() {
        this.sharedResource = Paths.get(tempDirPath, "ip-lock.shared").toFile();
        this.syncFile = Paths.get(tempDirPath, "ip-lock.lock").toFile();
        this.signalServerPort = DEFAULT_SIGNAL_SERVER_PORT;
        this.useLock = Boolean.TRUE;
        this.tryLock = Boolean.FALSE;
        this.skipUnlock = Boolean.FALSE;
        this.workDurationMs = DEFAULT_WORK_DURATION_MS;
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

    private static String determineClasspath() {
        return System.getProperty("java.class.path");
    }

    private static String determineTempDirPath() {
        return System.getProperty("java.io.tmpdir");
    }

    public void attachId(Integer id) {
        this.id = id;
    }

    public boolean hasIdAttached() {
        return this.id != null;
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

    public WorkerProcessBuilder workDurationMs(Long workDurationMs) {
        this.workDurationMs = workDurationMs;
        return this;
    }

    public WorkerProcessBuilder sharedResource(File sharedResource) {
        this.sharedResource = sharedResource;
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

    public ProcessHandle start() {
        ProcessBuilder pb = new ProcessBuilder(javaExecutablePath,
            "-classpath", javaClasspath, Worker.class.getName());
        pb.inheritIO();

        putEnv(pb, WorkerEnv.ID, id);
        putEnv(pb, WorkerEnv.SIGNAL_SERVER_PORT, signalServerPort);
        putEnv(pb, WorkerEnv.TRY_LOCK, tryLock);
        putEnv(pb, WorkerEnv.USE_LOCK, useLock);
        putEnv(pb, WorkerEnv.SKIP_UNLOCK, skipUnlock);
        putEnv(pb, WorkerEnv.WORK_DURATION_MS, workDurationMs);
        putEnv(pb, WorkerEnv.SHARED_RESOURCE_PATH, sharedResource.getAbsolutePath());
        putEnv(pb, WorkerEnv.SYNC_FILE_PATH, syncFile.getAbsolutePath());
        if (this.breakpoint != null) {
            putEnv(pb, WorkerEnv.BREAKPOINT, breakpoint);
        }

        try {
            Process process = pb.start();

            return new ProcessHandle(id, process);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void putEnv(ProcessBuilder pb, WorkerEnv var, Object val) {
        pb.environment().put(var.getVarName(), val.toString());
    }
}
