package util;

import equity.objectpooling.Order;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.List;
import java.util.concurrent.BlockingQueue;

public class HealthCheck {
    private static final Logger log = LogManager.getLogger(HealthCheck.class);
    private static final int QUEUE_WARNING_THRESHOLD = 3000;

    public static void checkQueueHealth(BlockingQueue<Order> orderQueue) {
        int size = orderQueue.size();
        if (size > QUEUE_WARNING_THRESHOLD) {
//            log.warn("Order queue size ({}) exceeds threshold. Possible processing bottleneck.", size);
        }
    }

    public static void printGCStats() {
        log.info("Java version: " + System.getProperty("java.version"));
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            log.info("GC in use: " + gcBean.getName());
            log.info("Collection count: {}", gcBean.getCollectionCount());
             log.info("Collection time: {} ms", gcBean.getCollectionTime());
        }
        Runtime runtime = Runtime.getRuntime();
        log.info("Memory: used = {}MB, free = {}MB, total = {}MB, max = {}MB",
                (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024,
                runtime.freeMemory() / 1024 / 1024,
                runtime.totalMemory() / 1024 / 1024,
                runtime.maxMemory() / 1024 / 1024);
    }

    public static void printJVMFlags() {
        RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
        List<String> arguments = runtimeMxBean.getInputArguments();

        if (arguments.isEmpty()) {
            log.info("No JVM arguments detected");
        } else {
            for (String arg : arguments) {
                log.info("JVM Arg: {}", arg);
            }
        }

        log.info("JVM Name: {}", runtimeMxBean.getVmName());
        log.info("JVM Version: {}", runtimeMxBean.getVmVersion());
        log.info("JVM Vendor: {}", runtimeMxBean.getVmVendor());
    }


    /**
     * Prints Log4j2 async logging related system properties.
     */
    public static void printLog4j2Properties() {
        log.info("--- LOG4J2 ASYNC CONFIGURATION ---");

        String[] log4j2Properties = {
            "log4j2.contextSelector",
            "AsyncLogger.RingBufferSize",
            "AsyncLogger.WaitStrategy",
            "log4j2.asyncLoggerConfigRingBufferSize",
            "log4j2.asyncLoggerConfigWaitStrategy",
            "log4j2.enable.threadlocals",
            "log4j2.enable.direct.encoders",
            "log4j2.asyncLoggerWaitStrategy",
            "log4j2.enable.jmx",
            "log4j2.asyncQueueFullPolicy",
            "log4j2.discardThreshold"
        };

        boolean hasAsyncConfig = false;
        for (String prop : log4j2Properties) {
            String value = System.getProperty(prop);
            if (value != null) {
                log.info("Log4j2: {} = {}", prop, value);
                hasAsyncConfig = true;
            }
        }

        if (!hasAsyncConfig) {
            log.warn("No Log4j2 async properties detected - logging may be synchronous");
        }

        // Check if async context selector is properly set
        String contextSelector = System.getProperty("log4j2.contextSelector");
        if (contextSelector != null && contextSelector.contains("AsyncLoggerContextSelector")) {
            log.info("✓ Global async logging is ENABLED");
        } else {
            log.warn("⚠ Global async logging is NOT enabled - only AsyncFile/AsyncLogger appenders will be async");
        }
    }


     /**
     * Quick health check focused on async logging configuration.
     */
    public static void printAsyncLoggingHealthCheck() {
        log.info("=== ASYNC LOGGING HEALTH CHECK ===");
        printLog4j2Properties();

        // Additional runtime checks
        RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
        List<String> jvmArgs = runtimeMxBean.getInputArguments();

        boolean hasAsyncJvmArgs = jvmArgs.stream()
            .anyMatch(arg -> arg.contains("log4j2.contextSelector") ||
                           arg.contains("AsyncLogger"));

        if (hasAsyncJvmArgs) {
            log.info("✓ Async logging JVM arguments detected in runtime");
        } else {
            log.warn("⚠ No async logging JVM arguments detected - check if system properties are set programmatically");
        }
    }
}



