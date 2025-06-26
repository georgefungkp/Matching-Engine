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
        log.info("JVM flags:");
        for (String arg : arguments) {
            log.info(arg);
        }
    }
}



