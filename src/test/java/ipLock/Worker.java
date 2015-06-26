package ipLock;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

/**
 * The Class Worker.
 */
public class Worker {

	private static int ID;

	/**
	 * The main method.
	 *
	 * @param args
	 *            the arguments
	 * @throws InterruptedException
	 * @throws IOException
	 */
	public static void main(String[] args) throws InterruptedException,
			IOException {
		ID = Integer.parseInt(args[0]);
		int workDurationMs = Integer.parseInt(args[1]);
		log("starting worker", System.out);
		/*
		 * Shared resource that should be accessed exclusively. For testing a
		 * simple file is used.
		 */
		File resource = new File(args[2]);

		lock();

		try {

			if (resource.exists()) {
				log("shared resource already exists", System.err);
				System.exit(-1);
			}

			log("creating shared resource");
			// TODO: create shared resource
			boolean createResult = resource.createNewFile();
			if (!createResult) {
				log("could not create file; maybe created by concurrent process?",
						System.err);
				System.exit(-1);
			}

			Thread.sleep(workDurationMs);

			log("deleting shared resource");
			boolean deleteResult = resource.delete();
			if (!deleteResult) {
				log("could not delete file; maybe deleted by concurrent process?",
						System.err);
				System.exit(-1);
			}
		} finally {
			unlock();
		}
	}

	/**
	 * Lock.
	 */
	private static void lock() {
		log("acquiring lock");
		// TODO: acquire lock
		log("acquired lock");
	}

	/**
	 * Unlock.
	 */
	private static void unlock() {
		// TODO: release lock
		log("releasing lock");
	}

	private static final void log(String msg, PrintStream s) {
		StringBuffer finalMsg = new StringBuffer();
		finalMsg.append("%04d - ");
		if (s == System.err) {
			finalMsg.append("ERROR: ");
		}
		finalMsg.append(msg).append('\n');

		s.printf(finalMsg.toString(), ID);
	}

	private static final void log(String msg) {
		log(msg, System.out);
	}
}
