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
import java.util.*;

/**
 * Created by aske on 28.06.15.
 */
public class WorkerProcessBuilder {

    private static String javaExecutablePath;

    private static String javaClasspath;

    static {
        javaExecutablePath = determineJavaExecutablePath();
        javaClasspath = determineClasspath();
    }

    private ProcessBuilder pb;

    private WorkerProcessBuilder(Integer id) {
        pb = new ProcessBuilder(javaExecutablePath,
                "-classpath", javaClasspath, Worker.class.getName());
        pb.inheritIO();
        pb.environment().put("IPL_ID", id.toString());
    }

    public WorkerProcessBuilder signalServerPort(Integer port) {
        pb.environment().put("IPL_SIGNAL_SERVER_PORT", port.toString());
        return this;
    }

    public WorkerProcessBuilder tryLock(Boolean tryLock) {
        pb.environment().put("IPL_TRY_LOCK", tryLock.toString());
        return this;
    }

    public WorkerProcessBuilder useLock(Boolean useLock) {
        pb.environment().put("IPL_USE_LOCK", useLock.toString());
        return this;
    }

    public WorkerProcessBuilder skipUnlock(Boolean skipUnlock) {
        pb.environment().put("IPL_SKIP_UNLOCK", skipUnlock.toString());
        return this;
    }

    public WorkerProcessBuilder workDurationMs(Long workDurationMs) {
        pb.environment().put("IPL_WORK_DURATION_MS", workDurationMs.toString());
        return this;
    }

    public WorkerProcessBuilder resourcePath(String resourcePath) {
        pb.environment().put("IPL_RESOURCE_PATH", resourcePath);
        return this;
    }

    public WorkerProcessBuilder syncFilePath(String syncFilePath) {
        pb.environment().put("IPL_SYNC_FILE_PATH", syncFilePath);
        return this;
    }

    public WorkerProcessBuilder activateBreakpoints(WorkerBreakpoint... breakpoints) {
        List<String> nameList = new ArrayList<>();
        for (WorkerBreakpoint breakpoint : breakpoints) {
            nameList.add(breakpoint.name());
        }

        pb.environment().put("IPL_BREAKPOINTS", StringUtils.join(nameList, ':'));
        return this;
    }

    public Process start() throws IOException {
        return pb.start();
    }


    public static WorkerProcessBuilder builder(Integer id) {
        return new WorkerProcessBuilder(id);
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

}
