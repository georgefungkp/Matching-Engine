package util;

import equity.objectpooling.Order;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.BlockingQueue;

public class HealthCheck {
    private static final Logger log = LogManager.getLogger(HealthCheck.class);
    private static final int QUEUE_WARNING_THRESHOLD = 100;

    public static void checkQueueHealth(BlockingQueue<Order> orderQueue) {
        int size = orderQueue.size();
        if (size > QUEUE_WARNING_THRESHOLD) {
            log.warn("Order queue size ({}) exceeds threshold. Possible processing bottleneck.", size);
        }
    }
}
