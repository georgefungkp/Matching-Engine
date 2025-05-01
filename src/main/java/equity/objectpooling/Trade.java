package equity.objectpooling;

public class Trade {
    private String buyBrokerID;
    private String sellBrokerID;
    private String buyOrderID;
    private String sellOrderID;
    private String stockNo;
    private Double executedPrice;
    private int executedQty;
    private String executionDateTime;

    Trade(String buyBrokerID, String sellBrokerID, String buyOrderID, String sellOrderID,
          String stockNo, Double executedPrice, int executedQty, String executionDateTime) {
        this.buyBrokerID = buyBrokerID;
        this.sellBrokerID = sellBrokerID;
        this.buyOrderID = buyOrderID;
        this.sellOrderID = sellOrderID;
        this.stockNo = stockNo;
        this.executedPrice = executedPrice;
        this.executedQty = executedQty;
        this.executionDateTime = executionDateTime;
    }

    public void updateForReuse(String buyBrokerID, String sellBrokerID, String buyOrderID, String sellOrderID,
                               String stockNo, Double executedPrice, int executedQty, String executionDateTime) {
        this.buyBrokerID = buyBrokerID;
        this.sellBrokerID = sellBrokerID;
        this.buyOrderID = buyOrderID;
        this.sellOrderID = sellOrderID;
        this.stockNo = stockNo;
        this.executedPrice = executedPrice;
        this.executedQty = executedQty;
        this.executionDateTime = executionDateTime;
    }

    @Override
    public String toString() {
        return (this.getExecutionDateTime() + this.getStockNo() + this.getExecutedPrice() + this.getExecutedQty());
    }

    public String getBuyBrokerID() {
        return buyBrokerID;
    }

    public String getSellBrokerID() {
        return sellBrokerID;
    }

    public String getBuyOrderID() {
        return buyOrderID;
    }

    public String getSellOrderID() {
        return sellOrderID;
    }

    public String getStockNo() {
        return stockNo;
    }

    public void setStockNo(String stockNo) {
        this.stockNo = stockNo;
    }

    public Double getExecutedPrice() {
        return executedPrice;
    }

    public int getExecutedQty() {
        return executedQty;
    }

    public String getExecutionDateTime() {
        return executionDateTime;
    }
}