package opus.logging;

public class Logging {
    public static boolean LOG_ENABLED = true;

    public static void logMessage(String log) {
        if (LOG_ENABLED) {
            System.out.println("SBLog: "+log);
        }
    }
}
