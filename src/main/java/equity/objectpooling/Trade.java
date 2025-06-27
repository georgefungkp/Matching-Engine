package equity.objectpooling;

import util.SequenceGenerator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Represents a trade execution between two orders in the equity trading system.
 * Optimized for precision and performance with BigDecimal pricing.
 */
public class Trade {

    // Immutable unique identifier for proper hashCode/equals
    private static final SequenceGenerator TRADE_OBJ_ID_GENERATOR = new SequenceGenerator();
    private final int tradeObjId;

    // Trade sequence generator and number
    private static final SequenceGenerator TRADE_SEQ_GENERATOR = new SequenceGenerator();
    private long tradeSeqNo;


    // Trade execution details (mutable for object pooling)
    private String buyBrokerID;
    private String sellBrokerID;
    private String buyOrderID;
    private String sellOrderID;
    private long internalBuyOrderSeqNo;
    private long internalSellOrderSeqNo;
    private String stockNo;
    private BigDecimal executedPrice;
    private int executedQty;
    private int buyOrderRemainingQty;
    private BigDecimal buyOrderAvgExecutedPrice;
    private int sellOrderRemainingQty;
    private BigDecimal sellOrderAvgExecutedPrice;
    private LocalDateTime executionDateTime;

    // Precision constants
    private static final int PRICE_SCALE = 4;
    private static final RoundingMode PRICE_ROUNDING = RoundingMode.HALF_UP;
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    // === Constructor ===
    Trade(Order bidOrder, Order askOrder, String stockNo, BigDecimal executedPrice, int executedQty, String executionDateTime) {
        this.tradeObjId = TRADE_OBJ_ID_GENERATOR.getNextSequence();
        updateTradeData(bidOrder, askOrder, stockNo, executedPrice, executedQty, executionDateTime);
    }

    // === Object Pool Support ===

    public void reset(Order bidOrder, Order askOrder, String stockNo,
                      BigDecimal executedPrice, int executedQty, String executionDateTime) {
        updateTradeData(bidOrder, askOrder, stockNo, executedPrice, executedQty, executionDateTime);
    }

    private void updateTradeData(Order bidOrder, Order askOrder, String stockNo,
                                BigDecimal executedPrice, int executedQty, String executionDateTime) {
        validateInputs(bidOrder, askOrder, stockNo, executedPrice, executedQty, executionDateTime);

        // Note: tradeObjId remains unchanged - preserving identity
        this.tradeSeqNo = TRADE_SEQ_GENERATOR.getNextSequence();
        this.buyBrokerID = bidOrder.getBrokerID();
        this.sellBrokerID = askOrder.getBrokerID();
        this.buyOrderID = bidOrder.getClientOrdID();
        this.sellOrderID = askOrder.getClientOrdID();
        this.internalBuyOrderSeqNo = bidOrder.getOrderSeqID();
        this.internalSellOrderSeqNo = askOrder.getOrderSeqID();
        this.stockNo = stockNo;
        this.executedPrice = executedPrice.setScale(PRICE_SCALE, PRICE_ROUNDING);
        this.executedQty = executedQty;
        this.buyOrderRemainingQty = bidOrder.getRemainingQty().get();
        this.buyOrderAvgExecutedPrice = getOrderAvgPriceAsBigDecimal(bidOrder);
        this.sellOrderRemainingQty = askOrder.getRemainingQty().get();
        this.sellOrderAvgExecutedPrice = getOrderAvgPriceAsBigDecimal(askOrder);
        this.executionDateTime = parseDateTime(executionDateTime);
    }

    // === Getters ===

    public int getTradeObjId() { return tradeObjId; }
    public long getTradeSeqNo() { return tradeSeqNo; }
    public String getBuyBrokerID() { return buyBrokerID; }
    public String getSellBrokerID() { return sellBrokerID; }
    public String getBuyOrderID() { return buyOrderID; }
    public String getSellOrderID() { return sellOrderID; }
    public String getStockNo() { return stockNo; }
    public BigDecimal getExecutedPrice() { return executedPrice; }
    public int getExecutedQty() { return executedQty; }
    public LocalDateTime getExecutionDateTime() { return executionDateTime; }
    public int getBuyOrderRemainingQty() { return buyOrderRemainingQty; }
    public BigDecimal getBuyOrderAvgExecutedPrice() { return buyOrderAvgExecutedPrice; }
    public int getSellOrderRemainingQty() { return sellOrderRemainingQty; }
    public BigDecimal getSellOrderAvgExecutedPrice() { return sellOrderAvgExecutedPrice; }
    public long getInternalBuyOrderSeqNo() { return internalBuyOrderSeqNo; }
    public long getInternalSellOrderSeqNo() { return internalSellOrderSeqNo; }


    // === Business Logic ===

    public BigDecimal getTradeValue() {
        return executedPrice.multiply(BigDecimal.valueOf(executedQty));
    }

    public boolean isBuyOrderCompletelyFilled() {
        return buyOrderRemainingQty == 0;
    }

    public boolean isSellOrderCompletelyFilled() {
        return sellOrderRemainingQty == 0;
    }

    public boolean areBothOrdersCompletelyFilled() {
        return isBuyOrderCompletelyFilled() && isSellOrderCompletelyFilled();
    }

    public String getExecutionDateTimeAsString() {
        return executionDateTime.format(DATETIME_FORMATTER);
    }

    // === Utility Methods ===

    private BigDecimal getOrderAvgPriceAsBigDecimal(Order order) {
        // Get BigDecimal average price from order's AtomicReference<BigDecimal>
        BigDecimal avgPrice = order.getAvgPrice().get();
        return avgPrice != null ? avgPrice.setScale(PRICE_SCALE, PRICE_ROUNDING) : BigDecimal.ZERO;
    }

    private LocalDateTime parseDateTime(String dateTimeString) {
        try {
            return LocalDateTime.parse(dateTimeString, DATETIME_FORMATTER);
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    private void validateInputs(Order bidOrder, Order askOrder, String stockNo,
                               BigDecimal executedPrice, int executedQty, String executionDateTime) {
        Objects.requireNonNull(bidOrder, "Bid order cannot be null");
        Objects.requireNonNull(askOrder, "Ask order cannot be null");
        Objects.requireNonNull(stockNo, "Stock number cannot be null");
        Objects.requireNonNull(executedPrice, "Executed price cannot be null");
        Objects.requireNonNull(executionDateTime, "Execution date time cannot be null");

        if (stockNo.trim().isEmpty()) {
            throw new IllegalArgumentException("Stock number cannot be empty");
        }
        if (executedPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Executed price must be positive: " + executedPrice);
        }
        if (executedQty <= 0) {
            throw new IllegalArgumentException("Executed quantity must be positive: " + executedQty);
        }
        if (!Objects.equals(bidOrder.getStockNo(), stockNo) || !Objects.equals(askOrder.getStockNo(), stockNo)) {
            throw new IllegalArgumentException("Order stock numbers must match trade stock number");
        }
    }

    // === Object Methods ===

    @Override
    public String toString() {
        return "Trade[" + tradeObjId + "]:" + getExecutionDateTimeAsString() +
               ":" + getStockNo() + ":" + getExecutedPrice() + ":" + getExecutedQty();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Trade trade = (Trade) obj;
        return tradeObjId == trade.tradeObjId;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(tradeObjId);
    }
}