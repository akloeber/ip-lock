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

/**
 * An inter-process lock for synchronization of multiple JVM based processes running on the same machine.
 * <p/>
 * If the process that owns the lock finishes without releasing it, the lock is released automatically. This is also
 * valid if the process owning the lock is destroyed or killed.
 * <p/>
 * The synchronization is implemented based on {@link FileLock}.
 * <p/>
 * This class is thread-safe: multiple threads can share a single
 * {@link IpLock} object without the need for external synchronization.
 *
 * @author Andreas Klöber
 * @see java.nio.channels.FileLock
 */
public class IpLock {

    /**
     * The synchronization file.
     */
    private File syncFile;

    /**
     * The underlying {@link FileLock} object.
     */
    private FileLock lock;

    /**
     * Create a new lock object that uses the given file for synchronization. The file will be created if it does not
     * exist.
     *
     * @param syncFile the file to be used for synchronization
     */
    public IpLock(final File syncFile) {
        this.syncFile = syncFile;
    }

    /**
     * Create a new lock object that uses the given file for synchronization. The file will be created if it does not
     * exist.
     *
     * @param syncFilePath path to the file to be used for synchronization
     */
    public IpLock(final String syncFilePath) {
        this(new File(syncFilePath));
    }

    /**
     * Creates a synchronization channel based on the given file. If the file does not exist yet, it is also created.
     *
     * @param file the underlying file
     * @return the created {@link FileChannel}
     * @throws IOException if the synchronization file could not be created,
     *                     e.g. because of missing write permissions in target folder
     */
    private static FileChannel createSyncChannel(final File file) throws IOException {
        // make sure sync file exists
        file.createNewFile();

        RandomAccessFile raFile = new RandomAccessFile(file, "rw");
        return raFile.getChannel();
    }

    /**
     * Acquires the lock in a blocking way.
     * <p/>
     * Only one process can acquire the lock at the same time. This method waits indefinitely until the lock could be
     * acquired.
     *
     * @throws IOException if the synchronization file could not be created,
     *                     e.g. because of missing write permissions in target folder
     */
    public void lock() throws IOException {
        synchronized (this) {
            this.lock = createSyncChannel(this.syncFile).lock();
        }
    }

    /**
     * Acquires the lock in a blocking way.
     * <p/>
     * Only one process can acquire the lock at the same time. In addition to {@link #lock()} this method also allows
     * configuration of a timeout.
     * <p/>
     * As the underlying {@link FileLock} object does not provide a way to cancel a lock request in case of a timeout,
     * this method periodically tries to get the lock until this is successful ot the timeout limit is reached.
     *
     * @param timeout         the timeout limit
     * @param tryLockInterval the time interval for trying locks
     * @param timeUnit        the {@link TimeUnit} for both <tt>timeout</tt> and <tt>tryLockInterval</tt> parameters
     * @return <code>true</code> if the lock could be required; <code>false</code> if there was a timeout
     * @throws IOException          if the synchronization file could not be created (e.g. because of missing write permissions
     *                              in target folder) or if some other I/O error occurs on the underlying {@link FileLock}
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
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

    /**
     * Tries to acquire the lock and returns immediately.
     * <p/>
     * Only one process can acquire the lock at the same time. The result determines whether the lock could be acquired
     * or not.
     *
     * @return <code>true</code> if the lock could be required; <code>false</code> if there was a timeout
     * @throws IOException if the synchronization file could not be created (e.g. because of missing write permissions
     *                     in target folder) or if some other I/O error occurs on the underlying {@link FileLock}
     */
    public boolean tryLock() throws IOException {
        synchronized (this) {
            this.lock = createSyncChannel(this.syncFile).tryLock();
            return this.lock != null;
        }
    }

    /**
     * Releases the lock.
     * <p/>
     * If the lock has not been acquired before, this method returns immediately.
     *
     * @throws IOException if some other I/O error occurs on the underlying {@link FileLock}
     */
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
