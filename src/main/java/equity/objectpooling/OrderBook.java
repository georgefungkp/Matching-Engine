package equity.objectpooling;

import equity.objectpooling.Order.Action;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;


/**
 * Represents an OrderBook for a particular stock, containing bid and ask order books.
 * Each price in the order book has a list of corresponding orders from different brokers.
 * Allows retrieval of the best bid price, best ask price, lowest bid price, and highest ask price.
 */
public class OrderBook {
    private static final Logger log = LogManager.getLogger(OrderBook.class);
    // Each price has its own list of brokers
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
     * Displays information related to orders in the specified order book based on the given buyOrSell parameter.
     * This method prints details such as stock number, description, best and last price, and order information at different price levels.
     *
     * @param buyOrSell the indicator for whether the operation is for buying ("B") or selling ("S")
     */
    public void showMap(String buyOrSell) {
        ConcurrentSkipListMap<BigDecimal, LinkedList<Order>> orderMap;
        ReentrantReadWriteLock readWriteLock;
        if (Action.getByValue(buyOrSell) == Action.BUY) {
            orderMap = bidMap;
            readWriteLock = bidLock;
        } else {
            orderMap = askMap;
            readWriteLock = askLock;
        }

        if (orderMap.isEmpty())
            return;
        readWriteLock.readLock().lock();
        log.debug("{}-{} {}", this.stockNo, this.desc, buyOrSell);
        log.debug("the first price: {}", orderMap.firstKey());
        log.debug("the last price: {}", orderMap.lastKey());
        for (Entry<BigDecimal, LinkedList<Order>> entry : orderMap.entrySet()) {
            log.debug("Orders of {} at price level {} ", stockNo, entry.getKey());
            entry.getValue().forEach(a -> log.debug("Broker ID {} Client Brk ID {} Qty {} ", a.getBrokerID(), a.getClientOrdID(), a.getQuantity()));
            log.debug("The time of head is {}", Objects.requireNonNull(entry.getValue().peek()).getCreatedDateTime());
        }
        readWriteLock.readLock().unlock();
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


    public void showMap() {
        showMap("B");
        showMap("S");
    }


    public ReentrantReadWriteLock getBidLock() {
        return bidLock;
    }

    public ReentrantReadWriteLock getAskLock() {
        return askLock;
    }

}
