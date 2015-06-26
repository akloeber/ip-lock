package ipLock;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.NameFileFilter;
import org.apache.commons.lang.StringUtils;
import org.junit.Before;
import org.junit.Test;

public class IpLockTest {

	private String javaExecutablePath;
	private String javaClasspath;
	private File sharedResource;

	@Before
	public void setup() throws IOException {
		javaExecutablePath = determineJavaExecutablePath();
		javaClasspath = determineClasspath();
		sharedResource = Paths.get(System.getProperty("java.io.tmpdir"),
				"ip-lock.lock").toFile();
		sharedResource.deleteOnExit();
	}

	@Test
	public void testMultipleProcesses() throws IOException,
			InterruptedException {
		System.out.printf("Shared resource is '%s'\n", sharedResource);

		List<ProcessBuilder> workers = createWorkers(3);
		List<Process> processes = startWorkers(workers);

		waitForProcesses(processes);
		assertSuccess(processes);
	}

	private void waitForProcesses(List<Process> processes)
			throws InterruptedException {
		for (Process process : processes) {
			process.waitFor();
		}
	}

	private void assertSuccess(List<Process> processes) {
		for (Process process : processes) {
			assertEquals(0, process.exitValue());
		}
	}

	private List<ProcessBuilder> createWorkers(int count) {
		List<ProcessBuilder> result = new ArrayList<ProcessBuilder>(count);

		for (int i = 0; i < count; i++) {
			result.add(createWorker(i, 10));
		}

		return result;
	}

	private List<Process> startWorkers(List<ProcessBuilder> workers)
			throws IOException {
		List<Process> result = new ArrayList<Process>(workers.size());

		for (ProcessBuilder pb : workers) {
			result.add(pb.start());
		}

		return result;
	}

	private ProcessBuilder createWorker(int id, int workDurationMs) {
		ProcessBuilder pb = new ProcessBuilder(javaExecutablePath,
				"-classpath", javaClasspath, "ipLock.Worker",
				Integer.toString(id), Integer.toString(workDurationMs),
				sharedResource.getAbsolutePath());
		pb.inheritIO();

		return pb;
	}

	private String determineJavaExecutablePath() throws IOException {
		File javaHome = new File(System.getProperty("java.home"));

		Collection<File> files = FileUtils.listFiles(javaHome,
				new NameFileFilter("java"), new NameFileFilter("bin"));

		if (files.isEmpty()) {
			throw new RuntimeException(
					"No java executable found at java home '" + javaHome + "'");
		}
		if (files.size() > 1) {
			throw new RuntimeException(
					"Multiple java executables found at java home '" + javaHome
							+ "': " + StringUtils.join(files, "; "));
		}

		return Collections.min(files).getAbsolutePath();
	}

	private String determineClasspath() {
		return System.getProperty("java.class.path");
	}
}
