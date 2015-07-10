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

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

public class WorkerManager implements SignalHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkerManager.class);

    private static final int SIGNAL_SERVER_PORT = 8080;

    private Map<Integer, ProcessHandle> workers;

    private SignalServer signalServer;

    private File sharedResource;

    private File syncFile;

    private int workerId;

    public WorkerManager() {
        workers = new HashMap<>();
        signalServer = new SignalServer();

        sharedResource = Paths.get(System.getProperty("java.io.tmpdir"),
            "ip-lock.shared").toFile();
        syncFile = Paths.get(System.getProperty("java.io.tmpdir"),
            "ip-lock.lock").toFile();

        sharedResource.deleteOnExit();
        syncFile.deleteOnExit();

        workerId = 1;
    }

    public void start() throws InterruptedException {
        // make sure everything is clean before starting new test
        sharedResource.delete();
        syncFile.delete();

        signalServer.start(SIGNAL_SERVER_PORT, this);
    }

    public void stop() throws InterruptedException {
        signalServer.stop();
    }

    public WorkerProcessBuilder builder() {
        return new WorkerProcessBuilder() {

            @Override
            public ProcessHandle start() throws IOException {
                if (!hasIdAttached()) {
                    // add incremented ID as it is not set yet
                    attachId(workerId++);
                }

                ProcessHandle p = super.start();

                workers.put(p.getId(), p);
                return p;
            }
        }
            .sharedResource(sharedResource)
            .syncFile(syncFile);
    }

    public void waitForBreakpoint(ProcessHandle workerProcess) throws InterruptedException {
        workerProcess.waitForBreakpoint();
    }

    public void await(ProcessHandle... pa) throws InterruptedException {
        Collection<ProcessHandle> waitPa = new HashSet<>();

        if (pa.length == 0) {
            // wait for all processes
            waitPa.addAll(workers.values());
        } else {
            // wait for given processes only
            Collections.addAll(waitPa, pa);
        }

        for (ProcessHandle p : waitPa) {
            p.getProcess().waitFor();
        }

    }

    public void signal(ProcessHandle p, ClientSignal signal) {
        switch (signal.getCode()) {
            case CONNECT:
                break;
            case BREAKPOINT:
                WorkerBreakpoint breakpoint = WorkerBreakpoint.valueOf(signal.getParams()[0]);
                p.signalBreakpoint(breakpoint);
                break;
        }
    }

    @Override
    public void handleSignal(ClientSignal signal) {
        LOGGER.info("received signal: {}", signal);
        signal(determineProcess(signal.getSenderId()), signal);
    }

    private ProcessHandle determineProcess(Integer id) {
        if (!workers.containsKey(id)) {
            throw new AssertionError(String.format("No process with id %d found", id));
        }

        return workers.get(id);
    }

    public void proceed(ProcessHandle p) throws InterruptedException {
        signalServer.send(new ServerSignal(p.getId(), SignalCode.PROCEED));
    }

    public void proceedToBreakpoint(ProcessHandle p, WorkerBreakpoint afterLock) {
    }
}
