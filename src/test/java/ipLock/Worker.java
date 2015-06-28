package ipLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.File;
import java.io.IOException;

/**
 * The Class Worker.
 */
public class Worker {

    private static final Logger LOGGER = LoggerFactory.getLogger(Worker.class);

    /**
     * The main method.
     *
     * @param args the arguments
     * @throws InterruptedException
     * @throws IOException
     */
    public static void main(String[] args) throws InterruptedException,
            IOException {
        MDC.put("IPL_ID", System.getenv().get("IPL_ID"));

        int workDurationMs = Integer.parseInt(System.getenv().get("IPL_WORK_DURATION_MS"));
        /*
         * Shared resource that should be accessed exclusively. For testing a
		 * simple file is used.
		 */
        File resource = new File(System.getenv().get("IPL_RESOURCE_PATH"));
        File syncFile = new File(System.getenv().get("IPL_SYNC_FILE_PATH"));
        boolean useLock = Boolean.valueOf(System.getenv().get("IPL_USE_LOCK"));
        boolean tryLock = Boolean.valueOf(System.getenv().get("IPL_TRY_LOCK"));
        boolean skipUnlock = Boolean.valueOf(System.getenv().get("IPL_SKIP_UNLOCK"));

        IpLock ipLock = new IpLock(syncFile);

        LOGGER.info("starting worker");
        if (useLock) {
            if (tryLock) {
                // try lock
                LOGGER.info("acquiring lock (try)");
                boolean gotLock = ipLock.tryLock();
                if (!gotLock) {
                    LOGGER.error("failed to acquire lock");
                    exit(WorkerExitCode.TRY_LOCK_FAILED);
                }
            } else {
                // block lock
                LOGGER.info("acquiring lock (block)");
                ipLock.lock();
            }
            LOGGER.info("acquired lock");
        }

        try {

            if (resource.exists()) {
                LOGGER.error("shared resource already exists; maybe created by concurrent process?");
                exit(WorkerExitCode.CONCURRENT_ACCESS_ERROR);
            }

            LOGGER.info("creating shared resource");
            boolean createResult = resource.createNewFile();
            if (!createResult) {
                LOGGER.error("could not create file; maybe created by concurrent process?");
                exit(WorkerExitCode.CONCURRENT_ACCESS_ERROR);
            }

            Thread.sleep(workDurationMs);

            LOGGER.info("deleting shared resource");
            boolean deleteResult = resource.delete();
            if (!deleteResult) {
                LOGGER.error("could not delete file; maybe deleted by concurrent process?");
                exit(WorkerExitCode.CONCURRENT_ACCESS_ERROR);
            }
        } finally {
            if (useLock && !skipUnlock) {
                LOGGER.info("releasing lock");
                ipLock.unlock();
            }
        }
    }

    private static void exit(WorkerExitCode exitCode) {
        System.exit(exitCode.getCode());
    }
}
