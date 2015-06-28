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
