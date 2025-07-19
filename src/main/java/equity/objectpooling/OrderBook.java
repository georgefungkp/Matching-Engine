package equity.objectpooling;

import equity.objectpooling.Order.Side;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;


/**
 * Represents an OrderBook for a particular stock, containing bid and ask order books.
 * Each price in the order book has a list of corresponding orders from different brokers.
 * Allows retrieval of the best bid price, best ask price, lowest bid price, and highest ask price.
 */
public class OrderBook {
    private static final Logger log = LogManager.getLogger(OrderBook.class);
    // Each price has its own list of the brokers
    // Bid order book
    private final ConcurrentSkipListMap<BigDecimal, LinkedList<Order>> bidMap = new ConcurrentSkipListMap<>();
    // Ask order book
    private final ConcurrentSkipListMap<BigDecimal, LinkedList<Order>> askMap = new ConcurrentSkipListMap<>(Comparator.reverseOrder());

    private final ReentrantReadWriteLock bidLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock askLock = new ReentrantReadWriteLock();

    private final String stockNo;
    private final String desc;

    public OrderBook(String stockNo, String desc) {
        log.debug("Creating order book of {}", desc);
        this.stockNo = stockNo;
        this.desc = desc;
    }


    public String getTextFormatOfOrderBook(Side side) {
        ConcurrentSkipListMap<BigDecimal, LinkedList<Order>> orderMap;

        if (side == Side.BUY) {
            orderMap = bidMap;
        } else {
            orderMap = askMap;
        }

        StringBuilder message = new StringBuilder();
        for (Entry<BigDecimal, LinkedList<Order>> entry : orderMap.entrySet()) {
            for (Order order : entry.getValue()) {
                message.append(order.getBrokerID()).append("-").append(order.getClientOrdID()).append(" ").append(order.getPrice().get()).append(" ").append(order.getRemainingQty()).append("\n");
            }
        }
        return message.toString();
    }


    /**
     * Displays the order map for the specified side (BUY or SELL) without applying any locking mechanism.
     * This method logs relevant details about the order map, such as the first and last price levels,
     * as well as details of the orders present at each price level. It also logs errors if any inconsistencies
     * or empty order lists are found.
     *
     * @param side the side of the order book to display, either BUY or SELL
     */
    public void showMapWithoutLocking(Side side) {
        ConcurrentSkipListMap<BigDecimal, LinkedList<Order>> orderMap;
        if (side == Side.BUY) {
            orderMap = bidMap;
        } else {
            orderMap = askMap;
        }

        if (orderMap.isEmpty())
            return;
        log.debug("{}-{} {}", this.stockNo, this.desc, side);
        log.debug("the first price: {}", orderMap.firstKey());
        log.debug("the last price: {}", orderMap.lastKey());
        for (Entry<BigDecimal, LinkedList<Order>> entry : orderMap.entrySet()) {
            log.debug("Orders of {} at price level {} ", stockNo, entry.getKey());
            if (entry.getValue() == null) {
                log.error("Empty list of orders at price level {} ", entry.getKey());
            }
            entry.getValue().forEach(a -> log.debug("{}-{} {}@{} ", a.getBrokerID(), a.getClientOrdID(), a.getRemainingQty(), a.getPrice().get()));
            entry.getValue().forEach(a -> {
                if (a.getPrice().get().compareTo(entry.getKey()) != 0)
                    log.error("Doesn't match {}: {}-{} {}@{} ", entry.getKey(), a.getBrokerID(), a.getClientOrdID(), a.getRemainingQty(), a.getPrice().get());
            });
//            log.debug("The time of head is {}", Objects.requireNonNull(entry.getValue().peek()).getCreatedDateTime());
        }
    }

    public void checkAndCleanUpPriceLevel(BigDecimal price, Side side) {
        if (price == null || side == null) {
            log.error("Null price or side in checkAndCleanUpPriceLevel");
            return;
        }

        ConcurrentSkipListMap<BigDecimal, LinkedList<Order>> orderMap = (side == Side.BUY) ? bidMap : askMap;
        // Clean up empty price levels
        LinkedList<Order> orderList = orderMap.get(price);
        if (orderList != null && orderList.isEmpty()) {
            orderMap.remove(price);
        }
    }


    public void showMap() {
        // Acquire locks in a consistent order
        bidLock.readLock().lock();
        try {
            askLock.readLock().lock();
            try {
                // Show the maps
                log.debug("Order Map of {} Best bid: {} Best ask: {}",
                        stockNo,
                        bidMap.isEmpty() ? null : bidMap.lastKey(),
                        askMap.isEmpty() ? null : askMap.lastKey());
                // Show bid and ask maps
                showMapWithoutLocking(Side.BUY);
                showMapWithoutLocking(Side.SELL);
                log.debug("\n");
            } finally {
                askLock.readLock().unlock();
            }
        } finally {
            bidLock.readLock().unlock();
        }
    }

    public String getStockNo() {
        return stockNo;
    }

    /**
     * Returns the best bid price from the bid order book.
     *
     * @return the best bid price or null if the bid order book is empty
     */
    public BigDecimal getBestBid() {
        bidLock.readLock().lock();
        try {
            return bidMap.isEmpty() ? null : bidMap.lastKey();
        } finally {
            bidLock.readLock().unlock();
        }
    }

    /**
     * Returns the best ask price from the ask order book.
     *
     * @return the best ask price or null if the ask order book is empty
     */
    public BigDecimal getBestAsk() {
        askLock.readLock().lock();
        try {
            return askMap.isEmpty() ? null : askMap.lastKey();
        } finally {
            askLock.readLock().unlock();
        }
    }

    /**
     * Returns the lowest bid price from the bid order book.
     *
     * @return the lowest bid price or null if the bid order book is empty
     */
    public BigDecimal getLowestBid() {
        bidLock.readLock().lock();
        try {
            return bidMap.isEmpty() ? null : bidMap.firstKey();
        } finally {
            bidLock.readLock().unlock();
        }
    }

    /**
     * Returns the highest ask price from the ask order book.
     *
     * @return the highest ask price or null if the ask order book is empty
     */
    public BigDecimal getHighestAsk() {
        askLock.readLock().lock();
        try {
            return askMap.isEmpty() ? null : askMap.firstKey();
        } finally {
            askLock.readLock().unlock();
        }
    }

    /**
     * Provides access to the read-write lock associated with the bid order book.
     *
     * @return the ReentrantReadWriteLock used to control read and write access to the bid order book
     */
    public ReentrantReadWriteLock getBidLock() {
        return bidLock;
    }

    /**
     * Provides access to the read-write lock associated with the ask order book.
     *
     * @return the ReentrantReadWriteLock used to control read and write access to the ask order book
     */
    public ReentrantReadWriteLock getAskLock() {
        return askLock;
    }

    /**
     * Retrieves the bid order book map, which is a sorted concurrent map with bid prices as keys and a list of corresponding bid orders as values.
     * The map allows for quick access to bid orders based on their price.
     *
     * @return ConcurrentSkipListMap containing bid prices and corresponding bid orders
     */
    public ConcurrentSkipListMap<BigDecimal, LinkedList<Order>> getBidMap() {
        return bidMap;
    }

    /**
     * Retrieves the ask order book map, which is a sorted concurrent map with ask prices as keys and a list of corresponding ask orders as values.
     * The map allows for quick access to ask orders based on their price.
     *
     * @return ConcurrentSkipListMap containing ask prices and corresponding ask orders
     */
    public ConcurrentSkipListMap<BigDecimal, LinkedList<Order>> getAskMap() {
        return askMap;
    }


}
