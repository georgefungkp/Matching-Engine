package equity.orderprocessing;

import equity.objectpooling.Order;
import equity.objectpooling.Order.Action;
import equity.objectpooling.Order.OrderType;
import equity.objectpooling.OrderBook;
import equity.objectpooling.OrderPoolManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantReadWriteLock;


public class OrderProcessingJob implements Runnable {
    private static final Logger log = LogManager.getLogger(OrderProcessingJob.class);
    private final LinkedBlockingQueue<Order> orderQueue;
    private final Map<String, OrderBook> orderBooks;
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
    public OrderProcessingJob(LinkedBlockingQueue<Order> orderQueue, Map<String, OrderBook> orderBooks, ConcurrentHashMap<String, Order> orderObjMapper) {
        this.orderQueue = orderQueue;
        this.orderBooks = orderBooks;
        this.orderObjMapper = orderObjMapper;
    }


    /**
     * Puts the given order into the corresponding order book based on the order type and buy/sell direction.
     *
     * @param order the order to be placed into the order book
     */
    public void putOrder(@NotNull Order order) {
        ConcurrentSkipListMap<Double, LinkedList<Order>> orderMap;
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
        // Set market order to be the best available price
        if ("M".equals(order.getOrderType()))
            if ("B".equals(order.getBuyOrSell()))
                order.setPrice(orderBook.getHighestAsk());
            else
                order.setPrice(orderBook.getLowestBid());

        // Put order to order book
        readWriteLock.writeLock().lock();
        if (orderMap.containsKey(order.getPrice().get())) {
            LinkedList<Order> orderList = orderMap.get(order.getPrice().get());
            if ("M".equals(order.getOrderType())){
                // Set double linked
                order.setNextOrder(orderList.getFirst());
                orderList.getFirst().setLastOrder(order);
                orderList.addFirst(order);
            }
            else {
                // Set double linked
                order.setLastOrder(orderList.getLast());
                orderList.getLast().setNextOrder(order);
                orderList.addLast(order);
            }
        } else {
            LinkedList<Order> orderList = new LinkedList<>();
            orderList.add(order);
            orderMap.put(order.getPrice().get(), orderList);
        }
        readWriteLock.writeLock().unlock();
        orderObjMapper.put(order.getBrokerID() + "-" + order.getClientOrdID(), order);
        log.info(order);
        if (LOG_ENABLED)
            orderBook.showMap();

    }


    /**
     * Removes the order identified by the broker ID and client order ID from the corresponding order book.
     * If the order is found and successfully removed, it is also removed from the order object mapper and returned to the order pool.
     *
     * @param brokerID the broker ID of the order to be removed
     * @param clientOrdId the client order ID of the order to be removed
     */
    public void removeOrder(String brokerID, String clientOrdId){
        Order order = orderObjMapper.get(brokerID + "-" + clientOrdId);
        ConcurrentSkipListMap<Double, LinkedList<Order>> orderMap;
        ReentrantReadWriteLock readWriteLock;
        OrderBook orderBook = orderBooks.get(order.getStockNo());
        if ("B".equals(order.getBuyOrSell())) {
            orderMap = orderBook.getBidMap();
            readWriteLock = orderBook.getBidLock();
        } else {
            orderMap = orderBook.getAskMap();
            readWriteLock = orderBook.getAskLock();
        }
        readWriteLock.writeLock().lock();
        if (!orderMap.get(order.getPrice().get()).remove(order))
            log.error("System cannot find the order {}-{}", order.getBrokerID(), order.getClientOrdID());
        // Remove the price in the map if list of order is exhausted
        if (orderMap.get(order.getPrice().get()).isEmpty())
            orderMap.pollLastEntry();

        readWriteLock.writeLock().unlock();

        orderObjMapper.remove(brokerID + "-" + clientOrdId);
        OrderPoolManager.returnOrderObj(order);

    }

    /**
     * Updates the specified order with the provided price and/or quantity.
     *
     * @param brokerID the broker ID of the order to update
     * @param clientOrdId the client order ID of the order to update
     * @param price the new price for the order, can be null if not updating price
     * @param quantity the new quantity for the order, can be null if not updating quantity
     */
    public void updateOrder(String brokerID, String clientOrdId, Double price, Integer quantity){
        Order order = orderObjMapper.get(brokerID + "-" + clientOrdId);
        if (quantity != null)
            order.setQuantity(quantity);
        if (price != null) {
            Order newOrder = OrderPoolManager.requestOrderObj(order.getStockNo(), order.getBrokerID(), order.getClientOrdID(),
                    OrderType.getByValue(order.getOrderType()), Action.getByValue(order.getBuyOrSell()),
                    price, order.getQuantity().get());
            removeOrder(brokerID, clientOrdId);

            putOrder(newOrder);
        }
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
                putOrder(order);
            } catch (InterruptedException e) {
                log.debug("{} is going down.", this.getClass().getSimpleName());
                isInterrupted = true;
            }
        }
    }
}
