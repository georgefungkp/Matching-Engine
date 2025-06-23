package util;

import io.github.cdimascio.dotenv.Dotenv;
import java.util.List;
import java.util.Arrays;


public class ReadConfig {
    private static final String TEST_CONFIG = "test.config";
    private static final String PROD_CONFIG = "dev.config";
    public static Dotenv dotenv;
    private static List<String> stocksList;

    static {
        String configFile = isTestEnvironment() ? TEST_CONFIG : PROD_CONFIG;
        dotenv = Dotenv.configure()
                .filename(configFile)
                .ignoreIfMissing()
                .load();
        initializeStocks();
    }

    private static void initializeStocks() {
        String stocksStr = dotenv.get("stocks");
        if (stocksStr != null) {
            // Remove the curly braces and split by comma
            stocksStr = stocksStr.replaceAll("[{}\"]", "").trim();
            stocksList = Arrays.asList(stocksStr.split("\\s*,\\s*"));
        }
    }

    public static List<String> getStocks() {
        return stocksList;
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