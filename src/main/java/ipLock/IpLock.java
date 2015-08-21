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
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class IpLock {

    private File syncFile;

    private FileLock lock;

    public IpLock(final File syncFile) {
        this.syncFile = syncFile;
    }

    public IpLock(final String syncFilePath) {
        this.syncFile = new File(syncFilePath);
    }

    private static FileChannel createSyncChannel(final File file) throws IOException {
        // make sure sync file exists
        file.createNewFile();

        RandomAccessFile raFile = new RandomAccessFile(file, "rw");
        return raFile.getChannel();
    }

    public void lock() throws IOException {
        synchronized (this) {
            this.lock = createSyncChannel(this.syncFile).lock();
        }
    }

    public boolean lock(long timeout, long tryLockInterval, TimeUnit timeUnit) throws IOException, InterruptedException {
        synchronized (this) {

            // schedule task for lock timeout
            final CountDownLatch timeoutSignal = new CountDownLatch(1);
            Timer lockTimeoutTimer = new Timer(true);
            TimerTask lockTimeoutTask = new TimerTask() {

                @Override
                public void run() {
                    timeoutSignal.countDown();
                }
            };

            try {
                lockTimeoutTimer.schedule(lockTimeoutTask, timeUnit.toMillis(timeout));

                while (this.lock == null) {
                    this.lock = createSyncChannel(this.syncFile).tryLock();
                    if (this.lock == null) {
                        // wait interval before next tryLock()
                        if (timeoutSignal.await(tryLockInterval, timeUnit)) {
                            // timeout signaled
                            return false;
                        }
                    }
                }

                return true;

            } finally {
                // unschedule task for lock timeout detection
                lockTimeoutTask.cancel();
                lockTimeoutTimer.cancel();
            }
        }
    }

    public boolean tryLock() throws IOException {
        synchronized (this) {
            this.lock = createSyncChannel(this.syncFile).tryLock();
            return this.lock != null;
        }
    }

    public void unlock() throws IOException {
        synchronized (this) {
            if (this.lock == null) {
                // there is no lock
                return;
            }

            this.lock.release();
        }
    }
}
