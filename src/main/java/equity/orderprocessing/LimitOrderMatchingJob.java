package equity.orderprocessing;

import equity.objectpooling.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final ConcurrentSkipListMap<BigDecimal, LinkedList<Order>> bidMap;
    private final ConcurrentSkipListMap<BigDecimal, LinkedList<Order>> askMap;
    private final LinkedBlockingQueue<MarketData> marketDataQueue;
    private final LinkedBlockingQueue<Trade> resultingTradeQueue;
    private final OrderProcessingJob orderProcessingJob;

    private final ConcurrentHashMap<String, Order> orderObjMapper;
    private boolean isInterrupted = false;


    /**
     * Creates a new LimitOrderMatchingJob for the specified order book.
     *
     * @param orderBook           The order book containing bid and ask orders for a specific stock
     * @param orderObjMapper      Map that keeps track of all active orders by their unique identifier
     * @param marketDataQueue     Queue for publishing market data updates
     * @param resultingTradeQueue Queue for publishing executed trades
     */
    public LimitOrderMatchingJob(OrderBook orderBook, ConcurrentHashMap<String, Order> orderObjMapper,
                                 LinkedBlockingQueue<MarketData> marketDataQueue,
                                 LinkedBlockingQueue<Trade> resultingTradeQueue,
                                 OrderProcessingJob orderProcessingJob) {
        this.orderBook = orderBook;
        this.stockNo = orderBook.getStockNo();
        this.bidMap = orderBook.getBidMap();
        this.askMap = orderBook.getAskMap();
        this.marketDataQueue = marketDataQueue;
        this.resultingTradeQueue = resultingTradeQueue;
        this.orderObjMapper = orderObjMapper;
        this.orderProcessingJob = orderProcessingJob;
        log.debug("LimitOrderMatchingJob created for stock {}", stockNo);
    }


    /**
     * Main processing loop that continuously checks for and executes matching orders.
     * The thread pauses briefly between cycles to prevent CPU saturation.
     */
    @Override
    public void run() {
        log.info("Starting order matching for stock {}", stockNo);

        while (!isInterrupted) {
            try {
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
     * Determines whether order matching should be skipped based on the state of the order book.
     * This method checks if either the bid or ask map is empty or if the prices do not meet the matching criteria.
     *
     * @return true if order matching should be skipped (either the bid or ask map is empty,
     *         or the best bid price is less than the best ask price), false otherwise
     */
    private boolean shouldMatchingSkipped() {
        // Both bid and ask maps must have orders
        if (askMap.isEmpty() || bidMap.isEmpty()) {
            return true;
        }

        // For a match, best bid price must be >= best ask price
        return askMap.lastKey().compareTo(bidMap.lastKey()) > 0;
    }


    /**
     * Updates a single order after it has been partially or completely filled.
     * Calculates new average price, updates filled quantity, remaining quantity, and timestamp.
     *
     * @param order      the order to update
     * @param filledQty  the quantity filled in this trade
     * @param tradePrice the execution price of the trade
     * @param tradeTime  the time when the trade occurred
     */
    private void updateOrderAfterFill(Order order, int filledQty, BigDecimal tradePrice, ZonedDateTime tradeTime) {
        // Get current values safely
        BigDecimal currentAvgPrice = order.getOrderAvgPrice();
        int currentFilledQty = order.getOrderFilledQty();
        int currentQuantity = order.getRemainingQty().get();

        // Calculate new values
        int newFilledQty = currentFilledQty + filledQty;
        int newRemainingQty = currentQuantity - filledQty;
        BigDecimal newAvgPrice = calculateWeightedAveragePrice(currentAvgPrice, currentFilledQty, tradePrice, filledQty);

        // Update order atomically
        order.setFilledQty(newFilledQty);
        order.setRemainingQty(newRemainingQty);
        order.setAvgPrice(newAvgPrice);
        order.setLastEventDateTime(tradeTime);
    }


    /**
     * Updates the price of a market order to the current best bid/ask price if needed.
     * This is called after a partial fill to ensure the remaining quantity gets the latest market price.
     *
     * @param order the market order to potentially update
     */
    private void updateBestPriceOfMarketOrder(Order order) {
        // Check if the market order needs a price update
        if (!shouldUpdateMarketOrderPrice(order)) {
            return;
        }

        // Get the new market price based on an order direction
        BigDecimal newMarketPrice = order.isBuyOrder() ?
                orderBook.getBestAsk() : orderBook.getBestBid();

        // If no best price available, can't update
        if (newMarketPrice == null) {
            log.warn("Cannot update market order {}-{}: no best price available",
                    order.getBrokerID(), order.getClientOrdID());
            return;
        }

        // If the price hasn't changed, no need to update
        if (newMarketPrice.equals(order.getPrice().get())) {
            return;
        }

        // Use OrderProcessingJob to update the order price
        boolean updated = orderProcessingJob.updateOrder(
                order.getBrokerID(),
                order.getClientOrdID(),
                newMarketPrice,
                null // Don't change quantity, only price
        );

        if (updated) {
            log.info("Updated market order {}-{} price from {} to {}",
                    order.getBrokerID(), order.getClientOrdID(),
                    order.getPrice().get(), newMarketPrice);
        } else {
            log.warn("Failed to update market order {}-{} price",
                    order.getBrokerID(), order.getClientOrdID());
        }
    }


    /**
     * Determines if a market order's price should be updated.
     * A market order should be updated if its current price is no longer the best available price.
     *
     * @param order the market order to check
     * @return true if the order price should be updated, false otherwise
     */
    private boolean shouldUpdateMarketOrderPrice(Order order) {
        BigDecimal currentPrice = order.getPrice().get();
        if (currentPrice == null)
            return false;

        if (order.getRemainingQty().get() == 0)
            return false;

        if (order.isBuyOrder()) {
            // For buy market orders, check if current price matches best ask
            BigDecimal bestAsk = orderBook.getBestAsk();
            return bestAsk != null && !currentPrice.equals(bestAsk);
        } else {
            // For sell market orders, check if current price matches best bid
            BigDecimal bestBid = orderBook.getBestBid();
            return bestBid != null && !currentPrice.equals(bestBid);
        }
    }


    /**
     * Calculates the weighted average price after a new fill.
     * Formula: (previousTotal + newFillValue) / newTotalQuantity
     *
     * @param currentAvgPrice  the current average price
     * @param currentFilledQty the current filled quantity
     * @param tradePrice       the price of the new fill
     * @param filledQty        the quantity of the new fill
     * @return the new weighted average price
     */
    private BigDecimal calculateWeightedAveragePrice(BigDecimal currentAvgPrice, int currentFilledQty,
                                                     BigDecimal tradePrice, int filledQty) {
        // Precision constants
        final int PRICE_SCALE = 4;
        final RoundingMode PRICE_ROUNDING = RoundingMode.HALF_UP;

        if (currentFilledQty == 0) {
            // The first fill - average price is the trade price
            return tradePrice.setScale(PRICE_SCALE, PRICE_ROUNDING);
        }

        // Calculate previous total value: currentAvgPrice * currentFilledQty
        BigDecimal previousTotal = currentAvgPrice.multiply(BigDecimal.valueOf(currentFilledQty));

        // Calculate new fill value: tradePrice * filledQty
        BigDecimal newFillValue = tradePrice.multiply(BigDecimal.valueOf(filledQty));

        // Calculate new total quantity
        BigDecimal newTotalQty = BigDecimal.valueOf(currentFilledQty + filledQty);

        // Calculate weighted average: (previousTotal + newFillValue) / newTotalQty
        return previousTotal.add(newFillValue)
                .divide(newTotalQty, PRICE_SCALE, PRICE_ROUNDING);
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
        if (shouldMatchingSkipped()) {
            // Pause it is not enough liquidity
            TimeUnit.MILLISECONDS.sleep(PROCESSING_DELAY_MS);
            return;
        }

        // Variables to store trade data
        Order topBid;
        Order topAsk;
        int filledQty;
        BigDecimal tradePrice;

        // Acquire bid lock first to prevent deadlocks
        orderBook.getBidLock().writeLock().lock();
        try {
            // Then acquire ask lock
            orderBook.getAskLock().writeLock().lock();
            try {
                // Check again with locks held
                if (shouldMatchingSkipped()) {
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
                filledQty = Math.min(topBid.getRemainingQty().get(), topAsk.getRemainingQty().get());

                // Use ask price for the trade (price-time priority)
                tradePrice = askMap.lastEntry().getKey();

                // Update newer average price, updates filled quantity, remaining quantity, and timestamp of the order
                updateOrderAfterFill(topBid, filledQty, tradePrice, ZonedDateTime.now());
                updateOrderAfterFill(topAsk, filledQty, tradePrice, ZonedDateTime.now());

                // Create a trade record
                Trade trade = OrderPoolManager.requestTradeObj(
                        topBid, topAsk, orderBook.getStockNo(),
                        tradePrice,
                        filledQty,
                        LocalDateTime.now().toString());

                // Add trade to the queue
                resultingTradeQueue.put(trade);
                log.debug("Trade executed: {} shares of {} at ${} between {} and {}",
                        filledQty, stockNo, tradePrice, topBid.getBrokerID(), topAsk.getBrokerID());

                // Handle completed orders
                processCompletedOrder(topBid, bestBidOrderList);
                processCompletedOrder(topAsk, bestAskOrderList);

                if (topBid.isMarketOrder())
                    updateBestPriceOfMarketOrder(topBid);
                if (topAsk.isMarketOrder())
                    updateBestPriceOfMarketOrder(topAsk);

            } finally {
                // Always release ask lock before bid lock
                orderBook.getAskLock().writeLock().unlock();
            }
        } finally {
            // Always release bid lock
            orderBook.getBidLock().writeLock().unlock();
        }

        // Send market data update if a trade was executed
        sendMarketDataUpdate(tradePrice);
    }

    /**
     * Processes an order that may have been completely filled.
     * If the order quantity is zero, it is removed from the order list,
     * from the order map, and returned to the object pool.
     *
     * @param order     The order to check
     * @param orderList The list containing the order
     */
    private void processCompletedOrder(Order order, LinkedList<Order> orderList) {
        if (order.getRemainingQty().get() == 0) {
            Order completedOrder = orderList.pollFirst();
            if (completedOrder != null) {
                String orderKey = completedOrder.getBrokerID() + "-" + completedOrder.getClientOrdID();
                orderObjMapper.remove(orderKey);
                OrderPoolManager.returnOrderObj(completedOrder);
                log.debug("Completed order removed: {}", orderKey);
            }
        }
        // Clean up empty price levels
        if (orderList.isEmpty())
            if (order.isBuyOrder()) {
                bidMap.pollLastEntry();
            }else{
                askMap.pollLastEntry();
            }
    }

    /**
     * Sends a market data update with the current state of the order book
     * and the details of the last executed trade.
     *
     * @param tradePrice The price at which the last trade was executed
     * @throws InterruptedException if interrupted while adding to the queue
     */
    private void sendMarketDataUpdate(BigDecimal tradePrice) throws InterruptedException {
        // Capture market data snapshot under read locks
        BigDecimal bestBid;
        BigDecimal bestAsk;
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
