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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

public class IpLock {

    private File syncFile;

    private FileLock lock;

    public IpLock(File syncFile) {
        this.syncFile = syncFile;
    }

    public IpLock(String syncFilePath) {
        this.syncFile = new File(syncFilePath);
    }

    public void lock() throws IOException {
        synchronized (this) {
            // make sure sync file exists
            this.syncFile.createNewFile();

            FileChannel channel = createChannel(this.syncFile);

            this.lock = channel.lock();
        }
    }

    public boolean tryLock() throws IOException {
        synchronized (this) {
            // make sure sync file exists
            this.syncFile.createNewFile();

            FileChannel channel = createChannel(this.syncFile);

            this.lock = channel.tryLock();
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

    private static FileChannel createChannel(File file) throws FileNotFoundException {
        RandomAccessFile raFile = new RandomAccessFile(file, "rw");
        return raFile.getChannel();
    }
}
