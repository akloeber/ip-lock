package ipLock;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Class WorkerProcessID
 *
 * @author Andreas Klöber
 */
public class WorkerProcessId {

    private static AtomicInteger idCounter = new AtomicInteger(0);

    public static Integer next() {
        return idCounter.incrementAndGet();
    }
}
