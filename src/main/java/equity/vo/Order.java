package equity.vo;
import java.time.ZonedDateTime;

public class Order {
    private String stockNo;
    private final String brokerId;
    private final String clientOrdID;
    private final String orderType;
    private final String buyOrSell;
    private Double price;
    private int quantity;
    private final ZonedDateTime createdDateTime;
    private ZonedDateTime lastEventDateTime;

    public Order(String stockNo, String brokerId, String clientOrdID, String orderType,
                 String buyOrSell, Double price, int quantity, ZonedDateTime createdDateTime, ZonedDateTime lastEventDateTime){
        this.stockNo = stockNo;
        this.brokerId = brokerId;
        this.clientOrdID = clientOrdID;
        this.orderType = orderType;
        this.buyOrSell = buyOrSell;
        this.price = price;
        this.quantity = quantity;
        this.createdDateTime = createdDateTime;
        this.lastEventDateTime = lastEventDateTime;
    }

    // getters
    public String getStockNo() { return stockNo; }

    public String getBrokerId() { return brokerId; }

    public String getClientOrdID() { return clientOrdID; }

    public String getOrderType() { return orderType; }

    public String getBuyOrSell() { return buyOrSell; }

    public Double getPrice() { return price; }

    public int getQuantity() { return quantity; }

    public ZonedDateTime getCreatedDateTime() { return createdDateTime; }

    // setters
    public void setStockNo(String stockNo) { this.stockNo = stockNo; }

    public void setPrice(Double price) { this.price = price; }

    public void setQuantity(int quantity) { this.quantity = quantity; }

    public void setLastEventDateTime(ZonedDateTime lastEventDateTime) { this.lastEventDateTime = lastEventDateTime; }

    @Override
    public String toString() {
        return "Order{" +
                "stockNo='" + stockNo + '\'' +
                ", brokerId='" + brokerId + '\'' +
                ", clientOrdID='" + clientOrdID + '\'' +
                ", orderType='" + orderType + '\'' +
                ", buyOrSell='" + buyOrSell + '\'' +
                ", price=" + price +
                ", quantity=" + quantity +
                ", createdDateTime=" + createdDateTime +
                ", lastEventDateTime=" + lastEventDateTime +
                '}';
    }

}