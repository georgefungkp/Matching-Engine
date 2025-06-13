package equity.orderprocessing;

import equity.objectpooling.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * LimitOrderMatchingJob is responsible for matching bid and ask orders for a specific stock.
 * Each instance runs in its own thread and continuously checks for matching orders that can be
 * executed as trades. When a match is found, it updates order quantities, creates trade records,
 * and publishes market data updates.
 */
public class LimitOrderMatchingJob implements Runnable {
    private static final Logger log = LogManager.getLogger(LimitOrderMatchingJob.class);
    private static final int PROCESSING_DELAY_MS = 1;
    private final String stockNo;
    private final OrderBook orderBook;
    private final ConcurrentSkipListMap<Double, LinkedList<Order>> bidMap;
    private final ConcurrentSkipListMap<Double, LinkedList<Order>> askMap;
    private final LinkedBlockingQueue<MarketData> marketDataQueue;
    private final LinkedBlockingQueue<Trade> resultingTradeQueue;

    private final ConcurrentHashMap<String, Order> orderObjMapper;
    private boolean isInterrupted = false;


    /**
     * Creates a new LimitOrderMatchingJob for the specified order book.
     *
     * @param orderBook The order book containing bid and ask orders for a specific stock
     * @param orderObjMapper Map that keeps track of all active orders by their unique identifier
     * @param marketDataQueue Queue for publishing market data updates
     * @param resultingTradeQueue Queue for publishing executed trades
     */
    public LimitOrderMatchingJob(OrderBook orderBook, ConcurrentHashMap<String, Order> orderObjMapper, 
                                LinkedBlockingQueue<MarketData> marketDataQueue, 
                                LinkedBlockingQueue<Trade> resultingTradeQueue) {
        this.orderBook = orderBook;
        this.stockNo = orderBook.getStockNo();
        this.bidMap = orderBook.getBidMap();
        this.askMap = orderBook.getAskMap();
        this.marketDataQueue = marketDataQueue;
        this.resultingTradeQueue = resultingTradeQueue;
        this.orderObjMapper = orderObjMapper;
        log.debug("LimitOrderMatchingJob created for stock {}", stockNo);
    }



    /**
     * Runs this operation.
     */
    /**
     * Main processing loop that continuously checks for and executes matching orders.
     * The thread pauses briefly between cycles to prevent CPU saturation.
     */
    @Override
    public void run() {
        log.info("Starting order matching for stock {}", stockNo);

        while (!isInterrupted) {
            try {
                // Pause between processing cycles
                TimeUnit.MILLISECONDS.sleep(PROCESSING_DELAY_MS);

                // Process any matching orders
                matchTopOrder();

            } catch (InterruptedException e) {
                log.info("Order matching interrupted for stock {}", stockNo);
                Thread.currentThread().interrupt(); // Preserve interrupt status
                isInterrupted = true;
            } catch (Exception e) {
                // Log error but continue processing
                log.error("Error matching orders for stock {}: {}", stockNo, e.getMessage(), e);
            }
        }

        log.info("Order matching stopped for stock {}", stockNo);
    }


    /**
     * Checks if matching limit orders are available for trade execution.
     * For a match to be possible, both the ask and bid maps must contain orders,
     * and the best bid price must be greater than or equal to the best ask price.
     *
     * @return true if matching orders are available, false otherwise
     */
    private boolean isLimitOrderAvailable() {
        // Both bid and ask maps must have orders
        if (askMap.isEmpty() || bidMap.isEmpty()) {
            return false;
        }

        // For a match, best bid price must be >= best ask price
        return askMap.lastKey().compareTo(bidMap.lastKey()) <= 0;
    }

    /**
     * Matches the top bid and ask orders to execute a trade when conditions are met.
     * This method:
     * 1. Acquires write locks on both bid and ask maps
     * 2. Checks if matching orders are available
     * 3. Calculates the filled quantity and updates order quantities
     * 4. Creates a trade record and notifies brokers by adding it to trade queue
     * 5. Removes completed orders and cleans up empty price levels
     * 6. Publishes market data updates
     *
     * @throws InterruptedException if a thread is interrupted while waiting
     */
    public void matchTopOrder() throws InterruptedException {
        // Quick check without locks first
        if (!isLimitOrderAvailable()) {
            return;
        }

        // Variables to store trade data
        Order topBid = null;
        Order topAsk = null;
        int filledQty = 0;
        Double tradePrice = null;

        // Acquire bid lock first to prevent deadlocks
        orderBook.getBidLock().writeLock().lock();
        try {
            // Then acquire ask lock
            orderBook.getAskLock().writeLock().lock();
            try {
                // Check again with locks held
                if (!isLimitOrderAvailable()) {
                    return;
                }

                // Get the lists of orders at the best price levels
                LinkedList<Order> bestBidOrderList = bidMap.lastEntry().getValue();
                LinkedList<Order> bestAskOrderList = askMap.lastEntry().getValue();

                // Get the top orders
                topBid = bestBidOrderList.peekFirst();
                topAsk = bestAskOrderList.peekFirst();

                // Validate orders exist
                if (topBid == null || topAsk == null) {
                    log.warn("Null order found when matching orders for stock {}", stockNo);
                    return;
                }

                // Calculate filled quantity
                filledQty = Math.min(topBid.getQuantity().get(), topAsk.getQuantity().get());

                // Use ask price for the trade (price-time priority)
                tradePrice = askMap.lastEntry().getKey();

                // Update order quantities
                topBid.setQuantity(topBid.getQuantity().get() - filledQty);
                topBid.setLastEventDateTime(ZonedDateTime.now());
                topAsk.setQuantity(topAsk.getQuantity().get() - filledQty);
                topAsk.setLastEventDateTime(ZonedDateTime.now());

                // Create trade record
                Trade trade = OrderPoolManager.requestTradeObj(
                        topBid.getBrokerID(), 
                        topAsk.getBrokerID(), 
                        topBid.getClientOrdID(), 
                        topAsk.getClientOrdID(),
                        orderBook.getStockNo(), 
                        tradePrice, 
                        filledQty, 
                        LocalDateTime.now().toString());

                // Add trade to queue
                resultingTradeQueue.put(trade);

                log.debug("Trade executed: {} shares of {} at ${} between {} and {}", 
                        filledQty, stockNo, tradePrice, topBid.getBrokerID(), topAsk.getBrokerID());

                // Handle completed orders
                processCompletedOrder(topBid, bestBidOrderList);
                processCompletedOrder(topAsk, bestAskOrderList);

                // Clean up empty price levels
                if (bestAskOrderList.isEmpty()) {
                    askMap.pollLastEntry();
                }
                if (bestBidOrderList.isEmpty()) {
                    bidMap.pollLastEntry();
                }
            } finally {
                // Always release ask lock before bid lock
                orderBook.getAskLock().writeLock().unlock();
            }
        } finally {
            // Always release bid lock
            orderBook.getBidLock().writeLock().unlock();
        }

        // Send market data update if a trade was executed
        if (tradePrice != null) {
            sendMarketDataUpdate(tradePrice);
        }
    }

    /**
     * Processes an order that may have been completely filled.
     * If the order quantity is zero, it is removed from the order list,
     * from the order map, and returned to the object pool.
     *
     * @param order The order to check
     * @param orderList The list containing the order
     */
    private void processCompletedOrder(Order order, LinkedList<Order> orderList) {
        if (order.getQuantity().get() == 0) {
            Order completedOrder = orderList.pollFirst();
            if (completedOrder != null) {
                String orderKey = completedOrder.getBrokerID() + "-" + completedOrder.getClientOrdID();
                orderObjMapper.remove(orderKey);
                OrderPoolManager.returnOrderObj(completedOrder);
                log.debug("Completed order removed: {}", orderKey);
            }
        }
    }

    /**
     * Sends a market data update with the current state of the order book
     * and the details of the last executed trade.
     *
     * @param tradePrice The price at which the last trade was executed
     * @throws InterruptedException if interrupted while adding to the queue
     */
    private void sendMarketDataUpdate(Double tradePrice) throws InterruptedException {
        // Capture market data snapshot under read locks
        Double bestBid;
        Double bestAsk;
        Timestamp timestamp = Timestamp.from(Instant.now());

        // Briefly acquire read locks to get current market state
        orderBook.getBidLock().readLock().lock();
        try {
            orderBook.getAskLock().readLock().lock();
            try {
                // Get the current best prices
                bestBid = orderBook.getBestBid();
                bestAsk = orderBook.getBestAsk();

                // Note: We reference the maps directly rather than copying them
                // This is safe because ConcurrentSkipListMap is thread-safe for reads
            } finally {
                orderBook.getAskLock().readLock().unlock();
            }
        } finally {
            orderBook.getBidLock().readLock().unlock();
        }

        // Create and queue market data update without holding any locks
        MarketData marketData = new MarketData(
                stockNo,
                bestBid,
                bestAsk,
                tradePrice,
                timestamp,
                bidMap,  // Safe because ConcurrentSkipListMap is thread-safe for reading
                askMap);

        // Add to queue (might block, but we're not holding any locks)
        marketDataQueue.put(marketData);

        log.debug("Market data published for stock {}: best bid={}, best ask={}, last trade={}", 
                stockNo, bestBid, bestAsk, tradePrice);
    }
}
