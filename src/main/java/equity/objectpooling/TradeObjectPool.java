package equity.objectpooling;

import equity.requesthandling.MatchingEngine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import util.SequenceGenerator;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public class TradeObjectPool {
    private static final Logger log = LogManager.getLogger(MatchingEngine.class);
    private final Map<Integer, Trade> tradeObjMap = new HashMap<>();
    private final Set<Trade> inUsedTradeObjList = ConcurrentHashMap.newKeySet();
    private final Set<Trade> freeTradeObjList = ConcurrentHashMap.newKeySet();
    private final String stockNo;
    private final SequenceGenerator sequenceGenerator = new SequenceGenerator();

    public TradeObjectPool(String stockNo) {
        this.stockNo = stockNo;
    }



    public synchronized Trade makeANewTrade(String buyBrokerID, String sellBrokerID, String buyOrderID, String sellOrderID,
                                                   String stockNo, Double executedPrice, int executedQty, String executionDateTim){
        Trade newTrade;
        if (freeTradeObjList.isEmpty()){
            newTrade = new Trade(buyBrokerID, sellBrokerID, buyOrderID, sellOrderID, stockNo, executedPrice, executedQty, executionDateTim);
        }else{
            Iterator<Trade> iterator = freeTradeObjList.iterator();
            newTrade = iterator.next();
            newTrade.updateForReuse(buyBrokerID, sellBrokerID, buyOrderID, sellOrderID, stockNo, executedPrice, executedQty, executionDateTim);
            freeTradeObjList.remove(newTrade);
        }
        inUsedTradeObjList.add(newTrade);
        tradeObjMap.put(sequenceGenerator.getNextSequence(), newTrade);
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
            freeTradeObjList.add(trade);
        }
    }

    public int getFreeTradeCount() {
        return freeTradeObjList.size();
    }

    public int getUsedTradeCount(){
        return inUsedTradeObjList.size();
    }

    public void resetPool(){
        tradeObjMap.clear();
        inUsedTradeObjList.clear();
        freeTradeObjList.clear();
    }
}
