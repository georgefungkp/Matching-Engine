package util;

import io.github.cdimascio.dotenv.Dotenv;

public class ReadConfig {
    private static final String TEST_CONFIG = "test.config";
    private static final String PROD_CONFIG = "prod.config";
    public static Dotenv dotenv;

    static {
        String configFile = isTestEnvironment() ? TEST_CONFIG : PROD_CONFIG;
        dotenv = Dotenv.configure()
                .filename(configFile)
                .ignoreIfMissing()
                .load();
    }

    private static boolean isTestEnvironment() {
        // Check if running from test
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            if (element.getClassName().startsWith("org.junit.") || 
                element.getClassName().startsWith("Test")) {
                return true;
            }
        }
        return false;
    }
}