package equity.orderprocessing;

import equity.objectpooling.*;
import equity.objectpooling.Order.Side;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * LimitOrderMatchingJob is responsible for matching bid and ask orders for a specific stock.
 * Each instance runs in its own thread and continuously checks for matching orders that can be
 * executed as trades. When a match is found, it updates order quantities, creates trade records,
 * and publishes market data updates.
 */
public class LimitOrderMatchingJob implements Runnable {
    private static final boolean LOG_ENABLED = false;
    private static final Logger log = LogManager.getLogger(LimitOrderMatchingJob.class);
    private static final int PROCESSING_DELAY_MS = 1;
    private final String stockNo;
    private final OrderBook orderBook;
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
     * or the best bid price is less than the best ask price), false otherwise
     */
    private boolean shouldSkipMatching() {
        NavigableMap<BigDecimal, LinkedList<Order>> bidMap = orderBook.getBidMap();
        NavigableMap<BigDecimal, LinkedList<Order>> askMap = orderBook.getAskMap();

        // Both bid and ask maps must have orders
        if (askMap.isEmpty() || bidMap.isEmpty()) {
            if (LOG_ENABLED)
                log.debug("Skipping matching for stock {}: bid empty={}, ask empty={}",
                        stockNo, bidMap.isEmpty(), askMap.isEmpty());
            return true;
        }
        BigDecimal bestAsk = askMap.lastKey();
        BigDecimal bestBid = bidMap.lastKey();
        // For a match, best bid price must be >= best ask price
        boolean shouldSkip = bestAsk.compareTo(bestBid) > 0;
        if (shouldSkip && LOG_ENABLED) {
            log.debug("No matching possible for stock {}: best bid {} < best ask {}",
                    stockNo, bestBid, bestAsk);
        }
        return shouldSkip;
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
     */
    public void matchTopOrder() throws InterruptedException {
        // Quick check without locks first
        if (shouldSkipMatching()) {
            // Pause it is not enough liquidity
            TimeUnit.MILLISECONDS.sleep(PROCESSING_DELAY_MS);
            return;
        }

        TradeExecution tradeExecution = executeTradeUnderLocks();
        if (tradeExecution != null) {
            sendMarketDataUpdate(tradeExecution.tradePrice());
        }
    }

    /**
     * Executes a trade while holding the necessary locks and returns the trade execution details.
     *
     * @return TradeExecution details if a trade was executed, null otherwise
     * @throws InterruptedException if interrupted while adding to queue
     */
    private TradeExecution executeTradeUnderLocks() throws InterruptedException {
        NavigableMap<BigDecimal, LinkedList<Order>> bidMap = orderBook.getBidMap();
        NavigableMap<BigDecimal, LinkedList<Order>> askMap = orderBook.getAskMap();

        // Acquire bid lock first to prevent deadlocks
        orderBook.getBidLock().writeLock().lock();
        try {
            // Then acquire ask lock
            orderBook.getAskLock().writeLock().lock();
            try {
                // Check again with locks held
                if (shouldSkipMatching()) {
                    return null;
                }

                return processMatchingOrders(bidMap, askMap);
            } finally {
                // Always release ask lock before bid lock
                orderBook.getAskLock().writeLock().unlock();
            }
        } finally {
            // Always release bid lock
            orderBook.getBidLock().writeLock().unlock();
        }
    }

    /**
     * Processes the matching orders and executes the trade.
     *
     * @param bidMap the bid order map
     * @param askMap the ask order map
     * @return TradeExecution details if successful, null otherwise
     * @throws InterruptedException if interrupted while adding to queue
     */
    private TradeExecution processMatchingOrders(NavigableMap<BigDecimal, LinkedList<Order>> bidMap,
                                                 NavigableMap<BigDecimal, LinkedList<Order>> askMap) throws InterruptedException {
        // Get the lists of orders at the best price levels
        Entry<BigDecimal, LinkedList<Order>> lastBidEntry = bidMap.lastEntry();
        Entry<BigDecimal, LinkedList<Order>> lastAskEntry = askMap.lastEntry();
        LinkedList<Order> bestBidOrderList = lastBidEntry.getValue();
        LinkedList<Order> bestAskOrderList = lastAskEntry.getValue();

        // Add null checks
        if (bestBidOrderList == null || bestAskOrderList == null) {
            log.error("Null order list found at price level for stock {}", stockNo);
            return null;
        }

        // Get the top orders
        Order topBid = bestBidOrderList.peekFirst();
        Order topAsk = bestAskOrderList.peekFirst();
        log.debug("{} {}", lastBidEntry.getKey(), lastAskEntry.getKey());
        log.debug("topBid {}", topBid);
        log.debug("topAsk {}", topAsk);
        if (topBid == null || topAsk == null) {
            log.error("Null order found when matching orders for stock {}", stockNo);
            return null;
        }

        return executeTrade(topBid, topAsk, bestBidOrderList, bestAskOrderList, lastBidEntry.getKey(), lastAskEntry.getKey());
    }

    /**
     * Executes the actual trade between two orders.
     *
     * @param topBid           the best bid order
     * @param topAsk           the best ask order
     * @param bestBidOrderList the list containing the best bid orders
     * @param bestAskOrderList the list containing the best ask orders
     * @param bidPrice         the bid price level
     * @param askPrice         the ask price level
     * @return TradeExecution details
     * @throws InterruptedException if interrupted while adding to the queue
     */
    private TradeExecution executeTrade(Order topBid, Order topAsk,
                                        LinkedList<Order> bestBidOrderList, LinkedList<Order> bestAskOrderList,
                                        BigDecimal bidPrice, BigDecimal askPrice) throws InterruptedException {
        ZonedDateTime matchTime = ZonedDateTime.now();

        // Calculate filled quantity
        int filledQty = Math.min(topBid.getRemainingQty().get(), topAsk.getRemainingQty().get());

        // Use ask price for the trade (price-time priority)
        BigDecimal tradePrice = topAsk.getPrice().get();

        if (tradePrice.compareTo(askPrice) != 0) {
            log.error("Trade price {} not matching with ask price {} for stock {}", tradePrice, askPrice, stockNo);
        }

        // Update orders after fill
        updateOrderAfterFill(topBid, filledQty, tradePrice, matchTime);
        updateOrderAfterFill(topAsk, filledQty, tradePrice, matchTime);

        // Create and queue trade
        Trade trade = OrderPoolManager.requestTradeObj(
                topBid, topAsk, orderBook.getStockNo(),
                tradePrice, filledQty, matchTime.toString());
        resultingTradeQueue.put(trade);

        log.debug("Trade executed: {} shares of {} at ${} between {} and {}",
                filledQty, stockNo, tradePrice, topBid.getBrokerID(), topAsk.getBrokerID());

        // Handle completed orders and cleanup
        processCompletedOrder(topBid, bestBidOrderList);
        if (bestBidOrderList.isEmpty()) {
            orderBook.getBidMap().remove(bidPrice);
        }

        processCompletedOrder(topAsk, bestAskOrderList);
        if (bestAskOrderList.isEmpty()) {
            orderBook.getAskMap().remove(askPrice);
        }

        // Update market order prices if needed
        if (topBid.isMarketOrder()) {
            updateBestPriceOfMarketOrder(topBid);
        }
        if (topAsk.isMarketOrder()) {
            updateBestPriceOfMarketOrder(topAsk);
        }

        return new TradeExecution(tradePrice, filledQty, matchTime);
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
        if (order == null || orderList == null) {  // Add null check
            log.error("Null order or orderList in processCompletedOrder");
            return;
        }
        if (order.getRemainingQty().get() == 0) {
            if (orderList.remove(order)) {
                String orderKey = order.getBrokerID() + "-" + order.getClientOrdID();
                orderObjMapper.remove(orderKey);
                OrderPoolManager.returnOrderObj(order);
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
    private void sendMarketDataUpdate(BigDecimal tradePrice) throws InterruptedException {
        MarketData marketData;
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
                // Create and queue market data update without holding any locks
                marketData = new MarketData(
                        stockNo,
                        bestBid,
                        bestAsk,
                        tradePrice,
                        timestamp,
                        orderBook.getTextFormatOfOrderBook(Side.BUY), // Clone part of an order book to store a snapshot
                        orderBook.getTextFormatOfOrderBook(Side.SELL) // Clone part of an order book to store a snapshot
                );
            } finally {
                orderBook.getAskLock().readLock().unlock();
            }
        } finally {
            orderBook.getBidLock().readLock().unlock();
        }
        // Add to queue (might block, but we're not holding any locks)
        marketDataQueue.put(marketData);
        log.debug("Market data published for stock {}: best bid={}, best ask={}, last trade={}",
                stockNo, bestBid, bestAsk, tradePrice);
    }
}