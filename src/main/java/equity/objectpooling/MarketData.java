package equity.objectpooling;

import java.math.BigDecimal;
import java.sql.Timestamp;

public record MarketData(String stockNo, BigDecimal bestBid, BigDecimal bestAsk, BigDecimal lastTradePrice,
                         Timestamp updatedTime, String bidMapOrdersStr, String askMapOrdersStr) {

}
