package equity.orderprocessing;

import equity.objectpooling.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.LinkedList;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class LimitOrderMatchingJob implements Runnable {
    private final String stockNo;
    private final OrderBook orderBook;
    private final TreeMap<Double, LinkedList<Order>> bidMap;
    private final TreeMap<Double, LinkedList<Order>> askMap;
    private final LinkedBlockingQueue<MarketData> marketDataQueue;
    private final LinkedBlockingQueue<Trade> resultingTradeQueue;

    private final ConcurrentHashMap<String, Order> orderObjMapper;
    private boolean isInterrupted = false;


    public LimitOrderMatchingJob(OrderBook orderBook, ConcurrentHashMap<String, Order> orderObjMapper, LinkedBlockingQueue<MarketData> marketDataQueue, LinkedBlockingQueue<Trade> resultingTradeQueue) {
        this.orderBook = orderBook;
        this.stockNo = orderBook.getStockNo();
        this.bidMap = orderBook.getBidMap();
        this.askMap = orderBook.getAskMap();
        this.marketDataQueue = marketDataQueue;
        this.resultingTradeQueue = resultingTradeQueue;
        this.orderObjMapper = orderObjMapper;
    }



    /**
     * Runs this operation.
     */
    @Override
    public void run() {
        while (!isInterrupted) {
            // Pause before next cycle
            try {
                TimeUnit.MILLISECONDS.sleep(1);
            } catch (InterruptedException e) {
                isInterrupted = true;
                continue;
            }

            try {
                matchTopOrder();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }


    public boolean isLimitOrderAvailable(){
        // if there is limiting order, go to the next cycle
        if (askMap.isEmpty() || bidMap.isEmpty())
            return false;
        // if bestBIDPrice < bestAskPrice, go to the next cycle
        return askMap.lastKey().compareTo(bidMap.lastKey()) <= 0;
    }

    /**
     * Matches the top bID and ask orders to settle the trade.
     * The method acquires locks for the bID and ask portions of the order book, fetches the top bID and ask orders,
     * calculates the filled quantity between them, updates the order quantities, sends market data,
     * notifies clients of the settled trade, and removes orders if they are completed.
     * Additionally, it cleans up the price entries in the bID and ask maps if necessary.
     *
     * @throws InterruptedException if a thread is interrupted while waiting
     */
    public void matchTopOrder() throws InterruptedException {
        if (!isLimitOrderAvailable())
            return;

        // BestBidPrice >= BestAskPrice
        orderBook.getBidLock().writeLock().lock();
        orderBook.getAskLock().writeLock().lock();
        LinkedList<Order> bestBidOrderList = bidMap.lastEntry().getValue();
        LinkedList<Order> bestAskOrderList = askMap.lastEntry().getValue();
        Order topBid = bestBidOrderList.peekFirst();
        Order topAsk = bestAskOrderList.peekFirst();

        assert topBid != null;
        assert topAsk != null;
        int filledQty = Math.min(topBid.getQuantity(), topAsk.getQuantity());

        topBid.setQuantity(topBid.getQuantity() - filledQty);
        topBid.setLastEventDateTime(ZonedDateTime.now());
        topAsk.setQuantity(topAsk.getQuantity() - filledQty);
        topAsk.setLastEventDateTime(ZonedDateTime.now());
        Double tradePrice = askMap.lastEntry().getKey();
        try {
            // Notify client that order is settled
            resultingTradeQueue.put(new Trade(topBid.getBrokerID(), topAsk.getBrokerID(), topBid.getClientOrdID(), topAsk.getClientOrdID(),
                    orderBook.getStockNo(), tradePrice, filledQty, LocalDateTime.now().toString()));

            // Remove order if it's totally filled
            if (topBid.getQuantity() == 0) {
                Order order = bestBidOrderList.pollFirst();
                if (order != null) {
                    orderObjMapper.remove(order.getBrokerID() + "-" + order.getClientOrdID());
                    OrderManager.returnOrder(order);
                }
            }
            if (topAsk.getQuantity() == 0) {
                Order order = bestAskOrderList.pollFirst();
                if (order != null) {
                    orderObjMapper.remove(order.getBrokerID() + "-" + order.getClientOrdID());
                    OrderManager.returnOrder(order);
                }
            }

            // Remove the price in the map if list of order is exhausted
            if (bestAskOrderList.isEmpty())
                askMap.pollLastEntry();
            if (bestBidOrderList.isEmpty())
                bidMap.pollLastEntry();

            // Downgrade to read lock for market data access
            orderBook.getBidLock().readLock().lock();
            orderBook.getAskLock().readLock().lock();
        }finally {
            orderBook.getBidLock().writeLock().unlock();
            orderBook.getAskLock().writeLock().unlock();
        }

        // Send market data
        try {
            marketDataQueue.put(new MarketData(stockNo, orderBook.getBestBid(), orderBook.getBestAsk(),
                    tradePrice, Timestamp.from(Instant.now()), bidMap, askMap));
        }finally{
            orderBook.getBidLock().readLock().unlock();
            orderBook.getAskLock().readLock().unlock();
            }

    }
}
