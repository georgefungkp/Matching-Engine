package equity.orderprocessing;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

/**
 * Record to hold trade execution details.
 */
public record TradeExecution(BigDecimal tradePrice, int filledQty, ZonedDateTime matchTime) {}