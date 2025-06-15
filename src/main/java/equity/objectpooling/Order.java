package equity.objectpooling;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class Order {
    // Core order identification
    private final String stockNo;
    private String brokerID;
    private String clientOrdID;
    private String orderType;
    private String buyOrSell;

    // Order pricing and quantities with thread-safe atomic operations
    private final AtomicReference<BigDecimal> price;
    private final AtomicInteger quantity;
    private AtomicInteger filledQty;
    private AtomicInteger remainingQty;
    private AtomicReference<BigDecimal> avgPrice;

    // Timestamps
    private ZonedDateTime createdDateTime;
    private ZonedDateTime lastEventDateTime;

    // Internal precision handling
    private static final int PRICE_SCALE = 4;
    private static final RoundingMode PRICE_ROUNDING = RoundingMode.HALF_UP;




    // === Constructors ===

    Order(String stockNo, String brokerID, String clientOrdID, OrderType orderType,
          Action buyOrSell, BigDecimal price, int quantity) {
        this(stockNo, brokerID, clientOrdID, orderType, buyOrSell, price, quantity,
             ZonedDateTime.now(), ZonedDateTime.now());
    }

    Order(String stockNo, String brokerID, String clientOrdID, OrderType orderType,
          Action buyOrSell, BigDecimal price, int quantity,
          ZonedDateTime createdDateTime, ZonedDateTime lastEventDateTime) {

        // Validation
        this.stockNo = Objects.requireNonNull(stockNo, "Stock number cannot be null");
        this.brokerID = Objects.requireNonNull(brokerID, "Broker ID cannot be null");
        this.clientOrdID = Objects.requireNonNull(clientOrdID, "Client order ID cannot be null");
        Objects.requireNonNull(orderType, "Order type cannot be null");
        Objects.requireNonNull(buyOrSell, "Buy/Sell action cannot be null");

        this.orderType = orderType.value;
        this.buyOrSell = buyOrSell.value;
        this.price = new AtomicReference<>(roundPrice(price));
        this.quantity = new AtomicInteger(Math.max(0, quantity));
        this.filledQty = new AtomicInteger(0);
        this.remainingQty = new AtomicInteger(quantity); // Initialize remaining = total quantity
        this.avgPrice = new AtomicReference<>(BigDecimal.ZERO);
        this.createdDateTime = Objects.requireNonNull(createdDateTime, "Created date time cannot be null");
        this.lastEventDateTime = Objects.requireNonNull(lastEventDateTime, "Last event date time cannot be null");
    }


    // === Object Pool Methods ===

    /**
     * Resets this order for reuse in object pooling.
     * Resets all mutable fields to new values while maintaining thread safety.
     */
    public void reset(String brokerID, String clientOrdID, OrderType orderType,
                      Action buyOrSell, BigDecimal price, int quantity) {
        Objects.requireNonNull(brokerID, "Broker ID cannot be null");
        Objects.requireNonNull(clientOrdID, "Client order ID cannot be null");
        Objects.requireNonNull(orderType, "Order type cannot be null");
        Objects.requireNonNull(buyOrSell, "Buy/Sell action cannot be null");

        this.brokerID = brokerID;
        this.clientOrdID = clientOrdID;
        this.orderType = orderType.value;
        this.buyOrSell = buyOrSell.value;
        this.price.set(roundPrice(price));
        this.quantity.set(Math.max(0, quantity));
        this.filledQty.set(0);
        this.remainingQty.set(quantity); // Reset remaining = total quantity
        this.avgPrice.set(BigDecimal.ZERO);
        ZonedDateTime now = ZonedDateTime.now();
        this.createdDateTime = now;
        this.lastEventDateTime = now;
    }


    @Override
    public String toString() {
        return "Order{" +
                "stockNo='" + stockNo + '\'' +
                ", brokerID='" + brokerID + '\'' +
                ", clientOrdID='" + clientOrdID + '\'' +
                ", orderType='" + orderType + '\'' +
                ", buyOrSell='" + buyOrSell + '\'' +
                ", price=" + price +
                ", quantity=" + quantity +
                ", createdDateTime=" + createdDateTime +
                ", lastEventDateTime=" + lastEventDateTime +
                '}';
    }

    // === Getter Methods ===

    public String getStockNo() { return stockNo; }
    public String getBrokerID() { return brokerID; }
    public String getClientOrdID() { return clientOrdID; }
    public String getOrderType() { return orderType; }
    public String getBuyOrSell() { return buyOrSell; }
    public AtomicReference<BigDecimal> getPrice() { return price; }
    public AtomicInteger getQuantity() { return quantity; }
    public AtomicInteger getFilledQty() { return filledQty; }
    public AtomicInteger getRemainingQty() { return remainingQty; }
    public AtomicReference<BigDecimal> getAvgPrice() { return avgPrice; }
    public ZonedDateTime getCreatedDateTime() { return createdDateTime; }
    public ZonedDateTime getLastEventDateTime() { return lastEventDateTime; }

   // === Setter Methods ===

    public void setPrice(BigDecimal price) {
        this.price.set(roundPrice(price));
        updateTimestamp();
    }

    public void setQuantity(int quantity) {
        int newQty = Math.max(0, quantity);
        this.quantity.set(newQty);
        updateTimestamp();
    }

    public void setFilledQty(int filledQty) {
        int newFilled = Math.max(0, filledQty);
        this.filledQty.set(newFilled);
        updateTimestamp();
    }

    public void setRemainingQty(int quantity) {
        int newQty = Math.max(0, quantity);
        this.remainingQty.set(newQty);
        updateTimestamp();
    }

    public void setAvgPrice(BigDecimal avgPrice) {
        this.avgPrice.set(roundPrice(avgPrice));
        updateTimestamp();
    }

    public void setLastEventDateTime(ZonedDateTime lastEventDateTime) {
        this.lastEventDateTime = Objects.requireNonNull(lastEventDateTime, "Last event date time cannot be null");
    }


    /**
    * Safely retrieves the current average price from an order.
    * Returns 0.0 if the average price is not set or null.
    *
    * @return the current average price or 0.0 if not set
    */
    public BigDecimal getOrderAvgPrice() {
        AtomicReference<BigDecimal> avgPriceRef = getAvgPrice();
        if (avgPriceRef == null) {
            // Initialize if not set
            setAvgPrice(BigDecimal.ZERO);
            return BigDecimal.ZERO;
        }

        BigDecimal avgPrice = avgPriceRef.get();
        return avgPrice != null ? avgPrice : BigDecimal.ZERO;
    }

    /**
    * Safely retrieves the current-filled quantity from an order.
    * Returns 0 if the filled quantity is not set or null.
    *
    * @return the current filled quantity or 0 if not set
    */
    public int getOrderFilledQty() {
        AtomicInteger filledQtyRef = getFilledQty();
        if (filledQtyRef == null) {
            // Initialize if not set
            setFilledQty(0);
            return 0;
        }
        return filledQtyRef.get();
    }


    /**
     * Convenience method to check if an order is completely filled.
     *
     * @return true if no remaining quantity
     */
    public boolean isCompletelyFilled() {
        return remainingQty.get() == 0;
    }

    /**
     * Convenience method to check if an order is partially filled.
     *
     * @return true if some quantity filled but not complete
     */
    public boolean isPartiallyFilled() {
        return filledQty.get() > 0 && remainingQty.get() > 0;
    }


    // === Private Utility Methods ===
    /**
     * Rounds price to appropriate precision for financial calculations.
     */
    private BigDecimal roundPrice(BigDecimal price) {
        if (price == null) return null;
        return price.setScale(PRICE_SCALE, PRICE_ROUNDING);

    }

    /**
     * Updates the last event timestamp.
     */
    private void updateTimestamp() {
        this.lastEventDateTime = ZonedDateTime.now();
    }

    // Add these methods to your existing Order class

    // === Order Type Check Methods ===

    /**
     * Checks if this order is a market order.
     * Market orders are executed immediately at the best available price.
     *
     * @return true if this is a market order, false otherwise
     */
    public boolean isMarketOrder() {
        return OrderType.MARKET.value.equals(this.orderType);
    }

    /**
     * Checks if this order is a limit order.
     * Limit orders are executed only at a specific price or better.
     *
     * @return true if this is a limit order, false otherwise
     */
    public boolean isLimitOrder() {
        return OrderType.LIMIT.value.equals(this.orderType);
    }

    // === Order Side Check Methods ===

    /**
     * Checks if this order is a buy order (bid).
     * Buy orders represent demand side in the market.
     *
     * @return true if this is a buy/bid order, false otherwise
     */
    public boolean isBuyOrder() {
        return Action.BUY.value.equals(this.buyOrSell);
    }

    /**
     * Checks if this order is a bid order.
     * Alias for isBuyOrder() - bid orders are buy orders.
     *
     * @return true if this is a bid order, false otherwise
     */
    public boolean isBidOrder() {
        return isBuyOrder();
    }

    /**
     * Checks if this order is a sell order (ask).
     * Sell orders represent supply side in the market.
     *
     * @return true if this is a sell/ask order, false otherwise
     */
    public boolean isSellOrder() {
        return Action.SELL.value.equals(this.buyOrSell);
    }

    /**
     * Checks if this order is an ask order.
     * Alias for isSellOrder() - ask orders are sell orders.
     *
     * @return true if this is an ask order, false otherwise
     */
    public boolean isAskOrder() {
        return isSellOrder();
    }

    // === Combined Check Methods ===

    /**
     * Gets the order side as a descriptive string.
     *
     * @return "BID" for buy orders, "ASK" for sell orders
     */
    public String getOrderSide() {
        return isBuyOrder() ? "BID" : "ASK";
    }

    /**
     * Gets the order type as a descriptive string.
     *
     * @return "MARKET" for market orders, "LIMIT" for limit orders
     */
    public String getOrderTypeDescription() {
        return isMarketOrder() ? "MARKET" : "LIMIT";
    }

    /**
     * Gets a combined description of order type and side.
     *
     * @return combined description like "MARKET BID", "LIMIT ASK", etc.
     */
    public String getOrderDescription() {
        return getOrderTypeDescription() + " " + getOrderSide();
    }

    public enum OrderType {
        MARKET("M", "Market"),
        LIMIT("L", "Limited");

        private static final Map<String, OrderType> internalMap = new HashMap<>();

        static {
            for (OrderType type : OrderType.values()) {
                internalMap.put(type.value, type);
            }
        }

        public final String value;
        public final String description;

        OrderType(String value, String description) {
            this.value = value;
            this.description = description;
        }

        public static OrderType getByValue(String value) {
            return internalMap.get(value);
        }

        @Override
        public String toString() {
            return this.description;
        }
    }

    public enum Action {
        BUY("B", "Buy"),
        SELL("S", "Sell");

        // Mapping String value to enum
        private static final Map<String, Action> internalMap = new HashMap<>();

        static {
            for (Action a : Action.values()) {
                internalMap.put(a.value, a);
            }
        }

        public final String value;
        public final String description;

        Action(String value, String description) {
            this.value = value;
            this.description = description;
        }

        // Static getter method for retrieving enum by value.
        public static Action getByValue(String value) {
            return internalMap.get(value);
        }

        @Override
        public String toString() {
            return this.description;
        }
    }

    

}