package ipLock;

public class ProcessSingleton {

    private static final IpLock LOCK = new IpLock("/tmp/ProcessSingleton.lock");

    public static void main(String[] args) throws Exception {
        if (!LOCK.tryLock()) {
            throw new IllegalStateException(
                "Another instance of this process is already running");
        }

        try {
            // do your stuff instead
            Thread.sleep(5000);
        } finally {
            LOCK.unlock();
        }
    }
}
