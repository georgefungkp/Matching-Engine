package equity.vo;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.PriorityQueue;
import java.util.TreeMap;

public record MarketData(String stockNo, BigDecimal bestBid, BigDecimal bestAsk, BigDecimal nominalPrice,
                         Timestamp updatedTime, TreeMap<BigDecimal, PriorityQueue<Order>> bidMap,
                         TreeMap<BigDecimal, PriorityQueue<Order>> askMap) {

}
