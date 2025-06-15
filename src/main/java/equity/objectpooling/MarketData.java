package equity.objectpooling;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.AbstractMap;
import java.util.LinkedList;

public record MarketData(String stockNo, BigDecimal bestBid, BigDecimal bestAsk, BigDecimal lastTradePrice,
                         Timestamp updatedTime, AbstractMap<BigDecimal, LinkedList<Order>> bidMap,
                         AbstractMap<BigDecimal, LinkedList<Order>> askMap) {

}
