package equity.orderprocessing;

import equity.vo.Order;
import equity.vo.OrderBook;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantReadWriteLock;


public class OrderProcessingJob implements Runnable {
    private static final Logger log = LogManager.getLogger(OrderProcessingJob.class);
    private final LinkedBlockingQueue<Order> orderQueue;
    private final HashMap<String, OrderBook> orderBooks;
    private final ConcurrentHashMap<String, Order> orderObjMapper;
    private static final boolean LOG_ENABLED = true;
    private boolean isInterrupted = false;

    /**
     * Constructs an OrderProcessingJob with the given parameters.
     *
     * @param orderQueue the queue holding incoming orders to be processed
     * @param orderBooks a map of order books for different stock numbers
     * @param orderObjMapper a mapping of order objects identified by broker ID and client order ID
     */
    public OrderProcessingJob(LinkedBlockingQueue<Order> orderQueue, HashMap<String, OrderBook> orderBooks, ConcurrentHashMap<String, Order> orderObjMapper) {
        this.orderQueue = orderQueue;
        this.orderBooks = orderBooks;
        this.orderObjMapper = orderObjMapper;
    }


    /**
     * Puts the given order into the corresponding order book based on the order type and buy/sell direction.
     *
     * @param order the order to be placed into the order book
     */
    public void putOrder(Order order) {
        TreeMap<Double, LinkedList<Order>> orderMap;
        ReentrantReadWriteLock readWriteLock;
        OrderBook orderBook = orderBooks.get(order.getStockNo());
        if (orderBook == null) {
            log.debug("System can't find order book of {}", order.getStockNo());
            return;
        }
        if ("B".equals(order.getBuyOrSell())) {
            orderMap = orderBook.getBidMap();
            readWriteLock = orderBook.getBidLock();
        } else {
            orderMap = orderBook.getAskMap();
            readWriteLock = orderBook.getAskLock();
        }

        // Put order to order book if it's limited order
        if ("L".equals(order.getOrderType())) {
            readWriteLock.writeLock().lock();
            if (orderMap.containsKey(order.getPrice())) {
                LinkedList<Order> orderList = orderMap.get(order.getPrice());
                orderList.add(order);
            } else {
                LinkedList<Order> orderList = new LinkedList<>();
                orderList.add(order);
                orderMap.put(order.getPrice(), orderList);
            }
            orderObjMapper.put(order.getBrokerId() + "-" + order.getClientOrdID(), order);
            readWriteLock.writeLock().unlock();
        }

        if (LOG_ENABLED)
            orderBook.showMap();

    }


    @Override
    public void run() {
        while (!isInterrupted) {
            log.debug("Getting order from Queue");
            try {
                Order order = orderQueue.take();
                if (!orderBooks.containsKey(order.getStockNo())) {
                    log.debug("Stock no is incorrect. Order is ignored");
                    continue;
                }
                this.putOrder(order);
            } catch (InterruptedException e) {
                log.debug("{} is going down.", this.getClass().getSimpleName());
                isInterrupted = true;
            }
        }
    }
}
