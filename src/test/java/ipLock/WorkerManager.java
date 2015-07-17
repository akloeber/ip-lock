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
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class WorkerManager {

    private static final int SIGNAL_SERVER_PORT = 8080;

    private Map<Integer, ProcessHandle> workers;

    private SignalServer signalServer;

    private File sharedResource;

    private File syncFile;

    private AtomicInteger workerId;

    public WorkerManager() {
        workers = Collections.synchronizedMap(new HashMap<Integer, ProcessHandle>());
        signalServer = new SignalServer();

        sharedResource = Paths.get(System.getProperty("java.io.tmpdir"),
            "ip-lock.shared").toFile();
        syncFile = Paths.get(System.getProperty("java.io.tmpdir"),
            "ip-lock.lock").toFile();

        sharedResource.deleteOnExit();
        syncFile.deleteOnExit();

        workerId = new AtomicInteger(0);
    }

    public void start() throws InterruptedException {
        // make sure everything is clean before starting new test
        sharedResource.delete();
        syncFile.delete();

        signalServer.start(SIGNAL_SERVER_PORT);
    }

    public void stop() throws InterruptedException {
        signalServer.stop();
    }

    public void cleanup() {
        for (ProcessHandle p : workers.values()) {
            p.kill();
        }
    }

    public WorkerProcessBuilder builder() {
        return new WorkerProcessBuilder() {

            @Override
            public ProcessHandle start() {
                if (!hasIdAttached()) {
                    // add incremented ID as it is not set yet
                    attachId(workerId.incrementAndGet());
                }

                final ProcessHandle p = super.start();
                p.setSignalDispatcher(new SignalDispatcher() {

                    @Override
                    public void dispatch(Signal sig) {
                        signalServer.sendSignal(p.getId(), sig);

                    }
                });
                signalServer.addSignalHandler(p.getId(), p);

                workers.put(p.getId(), p);
                return p;
            }
        }
            .sharedResource(sharedResource)
            .syncFile(syncFile);
    }

    public void await(ProcessHandle... pa) {
        Collection<ProcessHandle> waitPa = new HashSet<>();

        if (pa.length == 0) {
            // wait for all processes
            waitPa.addAll(workers.values());
        } else {
            // wait for given processes only
            Collections.addAll(waitPa, pa);
        }

        for (ProcessHandle p : waitPa) {
            p.waitFor();
        }

    }

}
