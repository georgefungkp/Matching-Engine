package equity.objectpooling;

import java.sql.Timestamp;
import java.util.AbstractMap;
import java.util.LinkedList;

public record MarketData(String stockNo, Double bestBid, Double bestAsk, Double lastTradePrice,
                         Timestamp updatedTime, AbstractMap<Double, LinkedList<Order>> bidMap,
                         AbstractMap<Double, LinkedList<Order>> askMap) {

}
