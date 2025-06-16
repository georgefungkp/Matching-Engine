package equity.orderprocessing;

import equity.objectpooling.Order;
import equity.objectpooling.Order.Action;
import equity.objectpooling.OrderBook;
import equity.objectpooling.OrderPoolManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;


/**
 * OrderProcessingJob is responsible for receiving orders from the order queue and adding them
 * to the appropriate order book. It handles order creation, deletion, and updates, ensuring
 * thread-safe modifications to the order books. Each order is added to either the bid or ask
 * side of the order book based on its buy/sell direction.
 *
 * This class runs as a separate thread, continuously monitoring the order queue for new orders.
 */
public class OrderProcessingJob implements Runnable {
    private static final Logger log = LogManager.getLogger(OrderProcessingJob.class);
    private static final boolean LOG_ENABLED = true;
    private static final String BUY_ORDER = "B";
    private static final String SELL_ORDER = "S";
    private static final String MARKET_ORDER = "M";

    private final LinkedBlockingQueue<Order> orderQueue;
    private final Map<String, OrderBook> orderBooks;
    private final ConcurrentHashMap<String, Order> orderObjMapper;
    private volatile boolean isInterrupted = false;

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
     * This method:
     * 1. Determines the appropriate order book and side (bid/ask)
     * 2. Sets price for market orders based on best available prices
     * 3. Acquires the appropriate lock to safely modify the order book
     * 4. Adds the order to the correct price level in the order book
     * 5. Updates the order object mapper for future reference
     *
     * @param order the order to be placed into the order book
     * @throws NullPointerException if the order is null
     */
    public void putOrder(@NotNull Order order) {
        Objects.requireNonNull(order, "Order cannot be null");

        // Declare variables at the top of the method
        ConcurrentSkipListMap<BigDecimal, LinkedList<Order>> orderMap;
        ReentrantReadWriteLock readWriteLock;

        // Get the order book for this stock
        OrderBook orderBook = orderBooks.get(order.getStockNo());
        if (orderBook == null) {
            log.warn("Cannot process order: no order book found for stock {}", order.getStockNo());
            return;
        }

        // Determine which order a book to use (bid or ask)
        if (order.isBuyOrder()) {
            orderMap = orderBook.getBidMap();
            readWriteLock = orderBook.getBidLock();
        } else {
            orderMap = orderBook.getAskMap();
            readWriteLock = orderBook.getAskLock();
        }

        // Handle market orders - set price based on best available in an opposite book
        if (order.isMarketOrder())
            order.setPrice(order.isBuyOrder()? orderBook.getBestAsk():orderBook.getBestBid());

        // Check if the price is available after setting the market order price
        if (order.getPrice().get() == null) {
            log.warn("Cannot process order: no price available for market order {}-{}", 
                    order.getBrokerID(), order.getClientOrdID());
            return;
        }

        // Add order to the order book under lock protection
        readWriteLock.writeLock().lock();
        try {
            BigDecimal orderPrice = order.getPrice().get();
            if (orderMap.containsKey(orderPrice)) {
                // Add to the existing price level
                LinkedList<Order> orderList = orderMap.get(orderPrice);

                if (order.isMarketOrder()) {
                    // Market orders go to the front of the queue (FIFO)
                    orderList.addFirst(order);
                } else {
                    // Limit orders go to the back of the queue (FIFO)
                    orderList.addLast(order);
                }
            } else {
                // Create a new price level
                LinkedList<Order> orderList = new LinkedList<>();
                orderList.add(order);
                orderMap.put(orderPrice, orderList);
            }

            // Add to the order map for lookup by ID
            orderObjMapper.put(order.getBrokerID() + "-" + order.getClientOrdID(), order);

            log.info("Added {} order: {}-{} {} {} @ ${} x {}",
                    Action.getByValue(order.getBuyOrSell()),
                    order.getBrokerID(),
                    order.getClientOrdID(),
                    order.getBuyOrSell(), 
                    order.getOrderType(), 
                    order.getPrice().get(),
                    order.getQuantity().get());
        } finally {
            readWriteLock.writeLock().unlock();
        }

        // Show the order book for debugging if enabled
        if (LOG_ENABLED) {
            orderBook.showMap();
        }
    }


    /**
     * Removes the order identified by the broker ID and client order ID from the corresponding order book.
     * If the order is found and successfully removed, it is also removed from the order object mapper 
     * and returned to the order pool.
     *
     * @param brokerID the broker ID of the order to be removed
     * @param clientOrdId the client order ID of the order to be removed
     * @param isRetain don't return to object pool
     * @return true if the order was successfully removed, false otherwise
     */
    public boolean removeOrder(String brokerID, String clientOrdId, boolean isRetain) {
        // Declare variables at the top of the method
        ConcurrentSkipListMap<BigDecimal, LinkedList<Order>> orderMap;
        ReentrantReadWriteLock readWriteLock;

        Order order = orderObjMapper.get(brokerID + "-" + clientOrdId);

        if (order == null) {
            log.warn("Cannot remove order: Order {}-{} not found in the system", brokerID, clientOrdId);
            return false;
        }

        OrderBook orderBook = orderBooks.get(order.getStockNo());
        if (orderBook == null) {
            log.warn("Cannot remove order: Order book for stock {} not found", order.getStockNo());
            return false;
        }

        BigDecimal orderPrice = order.getPrice().get();
        if (orderPrice == null) {
            log.warn("Cannot remove order: Order {}-{} has null price", brokerID, clientOrdId);
            return false;
        }

        // Determine if this is a bid or ask order
        if (order.isBidOrder()) {
            orderMap = orderBook.getBidMap();
            readWriteLock = orderBook.getBidLock();
        } else {
            orderMap = orderBook.getAskMap();
            readWriteLock = orderBook.getAskLock();
        }

        boolean removed;
        readWriteLock.writeLock().lock();
        try {
            LinkedList<Order> orderList = orderMap.get(orderPrice);

            if (orderList == null) {
                log.warn("Cannot remove order: No orders at price level {} for {}-{}", 
                        orderPrice, brokerID, clientOrdId);
                return false;
            }

            // Remove the order from the list
            removed = orderList.remove(order);

            if (!removed) {
                log.error("System cannot find the order {}-{} in the order list", brokerID, clientOrdId);
            } else {
                // If the price level is now empty, remove it from the map
                if (orderList.isEmpty()) {
                    orderMap.remove(orderPrice);
                }

                log.info("Removed order: {}-{} {} @ ${}", 
                        order.getBrokerID(), order.getClientOrdID(), 
                        order.getBuyOrSell(), orderPrice);
            }
        } finally {
            readWriteLock.writeLock().unlock();
        }

        // If order was removed from the book, also remove from the mapper and return to the object pool
        if (removed && !isRetain) {
            orderObjMapper.remove(brokerID + "-" + clientOrdId);
            OrderPoolManager.returnOrderObj(order);

            if (LOG_ENABLED) {
                orderBook.showMap();
            }
        }

        return removed;
    }

    /**
     * Updates the specified order with the provided price and/or quantity.
     * Price changes require removing and re-adding the order, while quantity
     * changes can be made in-place if no price change is required.
     *
     * @param brokerID the broker ID of the order to update
     * @param clientOrdId the client order ID of the order to update
     * @param price the new price for the order can be null if not updating price
     * @param quantity the new quantity for the order can be null if not updating quantity
     * @return true if the order was successfully updated, false otherwise
     */
    public boolean updateOrder(String brokerID, String clientOrdId, BigDecimal price, Integer quantity) {
        // Declare variables at the top of the method
        ReentrantReadWriteLock readWriteLock;

        Order order = orderObjMapper.get(brokerID + "-" + clientOrdId);

        if (order == null) {
            log.warn("Cannot update order: Order {}-{} not found in the system", brokerID, clientOrdId);
            return false;
        }

        if (price == null && quantity == null) {
            log.warn("Update order: No changes requested for order {}-{}", brokerID, clientOrdId);
            return true; // Nothing to update
        }

        // If only updating quantity, we can do it directly without removing/re-adding
        if (quantity != null) {
            if (quantity.equals(order.getRemainingQty().get())){
                log.warn("Cannot update order: Order {}-{} is partially filled", brokerID, clientOrdId);
                return false;
            }

            OrderBook orderBook = orderBooks.get(order.getStockNo());

            if (orderBook == null) {
                log.warn("Cannot update order: Order book for stock {} not found", order.getStockNo());
                return false;
            }

            if (order.isBidOrder()) {
                readWriteLock = orderBook.getBidLock();
            } else {
                readWriteLock = orderBook.getAskLock();
            }

            readWriteLock.writeLock().lock();
            try {
                order.setQuantity(quantity);
                log.info("Updated quantity for order {}-{} to {}", brokerID, clientOrdId, quantity);

                if (LOG_ENABLED) {
                    orderBook.showMap();
                }
            } finally {
                readWriteLock.writeLock().unlock();
            }
        }

        if (price != null) {
            // For price changes, we need to remove and re-add the order
            try {
                // First remove the old order
                if (!removeOrder(brokerID, clientOrdId, true)) {
                    return false;
                }

                // Then add the new order using new price
                order.setPrice(price);
                putOrder(order);

                log.info("Updated order {}-{} with new price=${} and quantity={}",
                        brokerID, clientOrdId,
                        price,
                        quantity != null ? quantity : order.getQuantity().get());

            } catch (Exception e) {
                log.error("Error updating order {}-{}: {}", brokerID, clientOrdId, e.getMessage(), e);
                return false;
            }
        }
        // Update successfully
        return true;
    }


    /**
     * Main processing loop that continuously takes orders from the queue and processes them.
     * Handles interruption gracefully and reports errors without stopping the processing thread.
     */
    @Override
    public void run() {
        log.info("Order processing job started");

        while (!isInterrupted) {
            try {
                // Wait for next order (blocking operation)
                log.debug("Waiting for orders from queue");
                Order order = orderQueue.take();

                // Validate order before processing
                if (order == null) {
                    log.warn("Received null order from queue, ignoring");
                    continue;
                }

                // Validate stock exists
                if (!orderBooks.containsKey(order.getStockNo())) {
                    log.warn("Received order for unknown stock {}, ignoring", order.getStockNo());
                    continue;
                }

                // Process the order
                log.debug("Processing order: {}-{} for stock {}", 
                        order.getBrokerID(), order.getClientOrdID(), order.getStockNo());
                putOrder(order);

            } catch (InterruptedException e) {
                log.info("Order processing job interrupted, shutting down");
                Thread.currentThread().interrupt(); // Preserve interrupt status
                isInterrupted = true;
            } catch (Exception e) {
                // Log error but continue processing other orders
                log.error("Error processing order: {}", e.getMessage(), e);
                try {
                    // Brief pause to prevent tight loop in case of persistent error
                    TimeUnit.MILLISECONDS.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    isInterrupted = true;
                }
            }
        }

        log.info("Order processing job stopped");
    }

    /**
     * Safely gracefully stops the order processing job.
     * This method can be called from another thread to request termination.
     */
    public void shutdown() {
        isInterrupted = true;
        log.info("Shutdown requested for order processing job");
    }
}
