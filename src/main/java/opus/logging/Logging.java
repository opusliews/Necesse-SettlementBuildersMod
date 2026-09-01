package opus.logging;

public final class Logging {
	public static boolean logEnabled = true;

	private Logging() {
	}

	public static void logMessage(String log) {
		if (logEnabled) {
			System.out.println("SBLog: " + log);
		}
	}
}
