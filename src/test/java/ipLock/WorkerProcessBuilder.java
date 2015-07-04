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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

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

    private WorkerBreakpoint[] breakpoints;

    public WorkerProcessBuilder() {
        this.sharedResource = Paths.get(tempDirPath, "ip-lock.shared").toFile();
        this.syncFile = Paths.get(tempDirPath, "ip-lock.lock").toFile();
        this.signalServerPort = DEFAULT_SIGNAL_SERVER_PORT;
        this.useLock = Boolean.TRUE;
        this.tryLock = Boolean.FALSE;
        this.skipUnlock = Boolean.FALSE;
        this.workDurationMs = DEFAULT_WORK_DURATION_MS;
        this.breakpoints = new WorkerBreakpoint[0];
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

    public WorkerProcessBuilder activateBreakpoints(WorkerBreakpoint... breakpoints) {
        this.breakpoints = breakpoints;
        return this;
    }

    public ProcessHandle start() throws IOException {
        ProcessBuilder pb = new ProcessBuilder(javaExecutablePath,
            "-classpath", javaClasspath, Worker.class.getName());
        pb.inheritIO();
        pb.environment().put("IPL_ID", id.toString());

        pb.environment().put("IPL_SIGNAL_SERVER_PORT", signalServerPort.toString());
        pb.environment().put("IPL_TRY_LOCK", tryLock.toString());
        pb.environment().put("IPL_USE_LOCK", useLock.toString());
        pb.environment().put("IPL_SKIP_UNLOCK", skipUnlock.toString());
        pb.environment().put("IPL_WORK_DURATION_MS", workDurationMs.toString());
        pb.environment().put("IPL_SHARED_RESOURCE_PATH", sharedResource.getAbsolutePath());
        pb.environment().put("IPL_SYNC_FILE_PATH", syncFile.getAbsolutePath());

        List<String> nameList = new ArrayList<>();
        for (WorkerBreakpoint breakpoint : breakpoints) {
            nameList.add(breakpoint.name());
        }

        pb.environment().put("IPL_BREAKPOINTS", StringUtils.join(nameList, ':'));

        Process process = pb.start();

        return new ProcessHandle(id, process);
    }
}
