package equity.objectpooling;

import equity.requesthandling.MatchingEngine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public class TradeObjectPool {
    private static final Logger log = LogManager.getLogger(MatchingEngine.class);
    private final Set<Trade> inUsedTradeObjList = ConcurrentHashMap.newKeySet();
    private final Set<Trade> freeTradeObjList = ConcurrentHashMap.newKeySet();
    private final String stockNo;

    public TradeObjectPool(String stockNo) {
        this.stockNo = stockNo;
    }


    public synchronized Trade makeANewTrade(Order bidOrder, Order askOrder, String stockNo, BigDecimal executedPrice, int executedQty, String executionDateTim){
        Trade newTrade;
        if (freeTradeObjList.isEmpty()){
            newTrade = new Trade(bidOrder, askOrder, stockNo, executedPrice, executedQty, executionDateTim);
        }else{
            Iterator<Trade> iterator = freeTradeObjList.iterator();
            newTrade = iterator.next();
            newTrade.reset(bidOrder, askOrder, stockNo, executedPrice, executedQty, executionDateTim);
            freeTradeObjList.remove(newTrade);
        }
        inUsedTradeObjList.add(newTrade);
        return newTrade;
    }


    /**
     * Free up the given Trade object back to the free trade list in the pool.
     *
     * @param trade the Trade object to be returned to the free trade list
     */
    public synchronized void returnTradeObj(Trade trade){
        if (trade != null){
            if (!inUsedTradeObjList.remove(trade))
                log.error("{} is not in use. Need to check ", trade.toString());
            else {
                freeTradeObjList.add(trade);
                log.debug("{} free trade objects", freeTradeObjList.size());
                log.debug("{} trade objects in use", inUsedTradeObjList.size());
            }
        }
    }

    public int getFreeTradeCount() {
        return freeTradeObjList.size();
    }

    public int getUsedTradeCount(){
        return inUsedTradeObjList.size();
    }

    public void resetPool(){
        inUsedTradeObjList.clear();
        freeTradeObjList.clear();
    }
}
