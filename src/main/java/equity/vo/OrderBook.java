package equity.vo;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;


/*
 * Each stock has 2 order books
 */
public class OrderBook {
    private static final Logger log = LogManager.getLogger(OrderBook.class);
    private static final boolean LOG_ENABLED = true;
    // Each price has its own list of brokers
    // Bid order book
    private final TreeMap<Double, LinkedList<Order>> bidMap = new TreeMap<>();
    // Ask order book
    private final TreeMap<Double, LinkedList<Order>> askMap = new TreeMap<>(Comparator.reverseOrder());


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

    public Double getBestBid() {
        bidLock.readLock().lock();
        try {
            return bidMap.isEmpty() ? null : bidMap.lastKey();
        } finally {
            bidLock.readLock().unlock();
        }
    }

    public Double getBestAsk() {
        askLock.readLock().lock();
        try {
            return askMap.isEmpty() ? null : askMap.lastKey();
        } finally {
            askLock.readLock().unlock();
        }
    }


    public TreeMap<Double, LinkedList<Order>> getBidMap() {
        return bidMap;
    }

    public TreeMap<Double, LinkedList<Order>> getAskMap() {
        return askMap;
    }


    public void showMap(){
        showMap("B");
        showMap("S");
    }

    public void showMap(String buyOrSell) {
        TreeMap<Double, LinkedList<Order>> orderMap;
        ReentrantReadWriteLock readWriteLock;
        if (buyOrSell.equals("B")) {
            orderMap = bidMap;
            readWriteLock = bidLock;
        }else {
            orderMap = askMap;
            readWriteLock = askLock;
        }

        readWriteLock.readLock().lock();
        log.debug("{}-{} {}", this.stockNo, this.desc, buyOrSell);
        log.debug("the highest priority price: {}", orderMap.lastKey());
        log.debug("the lowest priority price: {}", orderMap.firstKey());
        for (Entry<Double, LinkedList<Order>> entry : orderMap.entrySet()) {
            log.debug("Orders of {} at price level {} ", stockNo, entry.getKey());
            entry.getValue().forEach(a -> log.debug("Broker ID {} Client Brk ID {} Qty {} ", a.getBrokerId(), a.getClientOrdID(), a.getQuantity()));
            log.debug("The time of head is {}", Objects.requireNonNull(entry.getValue().peek()).getCreatedDateTime());
        }
        readWriteLock.readLock().unlock();
    }

    public ReentrantReadWriteLock getBidLock() {
        return bidLock;
    }

    public ReentrantReadWriteLock getAskLock() {
        return askLock;
    }

}
