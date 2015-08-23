package ipLock.example;

import ipLock.IpLock;

public class ExampleSingleton {

    public static void main(String[] args) throws Exception {
        IpLock lock = new IpLock("/tmp/ExampleSingleton.lock");

        if (!lock.tryLock()) {
            throw new IllegalStateException(
                "Another instance of this process is already running");
        }

        try {
            // do your stuff instead
            Thread.sleep(5000);
        } finally {
            lock.unlock();
        }
    }
}
