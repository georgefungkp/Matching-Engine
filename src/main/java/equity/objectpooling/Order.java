package equity.objectpooling;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

public class Order {
    private final String stockNo;
    private String brokerID;
    private String clientOrdID;
    private String orderType;
    private String buyOrSell;
    private Double price;
    private int quantity;
    private ZonedDateTime createdDateTime;
    private ZonedDateTime lastEventDateTime;

    private Order nextOder;
    private Order lastOrder;

    
    Order(String stockNo, String brokerID, String clientOrdID, OrderType orderType,
                 Action buyOrSell, Double price, int quantity){
        this(stockNo, brokerID, clientOrdID, orderType, buyOrSell, price, quantity, ZonedDateTime.now(), ZonedDateTime.now());
    }

    Order(String stockNo, String brokerID, String clientOrdID, OrderType orderType,
                 Action buyOrSell, Double price, int quantity, ZonedDateTime createdDateTime, ZonedDateTime lastEventDateTime){
        this.stockNo = stockNo;
        this.brokerID = brokerID;
        this.clientOrdID = clientOrdID;
        this.orderType = orderType.value;
        this.buyOrSell = buyOrSell.value;
        this.price = price;
        this.quantity = quantity;
        this.createdDateTime = createdDateTime;
        this.lastEventDateTime = lastEventDateTime;
    }


    public void updateForReuse(String brokerID, String clientOrdID, OrderType orderType,
                 Action buyOrSell, Double price, int quantity){
        this.brokerID = brokerID;
        this.clientOrdID = clientOrdID;
        this.orderType = orderType.value;
        this.buyOrSell = buyOrSell.value;
        this.price = price;
        this.quantity = quantity;
        this.createdDateTime = ZonedDateTime.now();
        this.lastEventDateTime = ZonedDateTime.now();
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

    public enum OrderType {
        MARKET("M", "Market"),
        LIMIT("L", "Limited");

        public final String value;
        public final String description;

        private static final Map<String, OrderType> internalMap = new HashMap<>();

        static {
            for (OrderType type : OrderType.values()) {
                internalMap.put(type.value, type);
            }
        }

        OrderType(String value, String description){
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

        public final String value;
        public final String description;

        // Mapping String value to enum
        private static final Map<String, Action> internalMap = new HashMap<>();

        static {
            for (Action a : Action.values()) {
                internalMap.put(a.value, a);
            }
        }

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

    // getters
    public String getStockNo() { return stockNo; }

    public String getBrokerID() { return brokerID; }

    public String getClientOrdID() { return clientOrdID; }

    public String getOrderType() { return orderType; }

    public String getBuyOrSell() { return buyOrSell; }

    public Double getPrice() { return price; }

    public int getQuantity() { return quantity; }

    public ZonedDateTime getCreatedDateTime() { return createdDateTime; }

    // setters

    public void setPrice(Double price) { this.price = price; }

    public void setQuantity(int quantity) { this.quantity = quantity; }

    public void setLastEventDateTime(ZonedDateTime lastEventDateTime) { this.lastEventDateTime = lastEventDateTime; }

}