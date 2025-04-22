package equity.vo;

import java.sql.Timestamp;
import java.util.LinkedList;
import java.util.TreeMap;

public record MarketData(String stockNo, Double bestBid, Double bestAsk, Double lastTradePrice,
                         Timestamp updatedTime, TreeMap<Double, LinkedList<Order>> bidMap,
                         TreeMap<Double, LinkedList<Order>> askMap) {

}
