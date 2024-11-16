package equity.vo;

import java.math.BigDecimal;

public class Trade {
	String buyBrokerID;
	String sellBrokerID;
	String stockNo;
	BigDecimal executedPrice;
	int executedQty;
	String executionDateTime;
	
	public Trade(String buyBrokerID, String sellBrokerID, String stockNo, BigDecimal executedPrice, int executedQty, String executionDateTime) {
		this.buyBrokerID = buyBrokerID;
		this.sellBrokerID = sellBrokerID;
		this.stockNo = stockNo;
		this.executedPrice = executedPrice;
		this.executedQty = executedQty;
		this.executionDateTime = executionDateTime;
	}
	
	
	public String getSellBrokerID() {
		return sellBrokerID;
	}
	public void setSellBrokerID(String sellBrokerID) {
		this.sellBrokerID = sellBrokerID;
	}
	public String getStockNo() {
		return stockNo;
	}
	public void setStockNo(String stockNo) {
		this.stockNo = stockNo;
	}
	public BigDecimal getPrice() {
		return executedPrice;
	}
	public void setPrice(BigDecimal price) {
		this.executedPrice = price;
	}
	public int getQuantity() {
		return executedQty;
	}
	public void setQuantity(int quantity) {
		this.executedQty = quantity;
	}
	public String getExecutionDateTime() {
		return executionDateTime;
	}
	public void setExecutionDateTime(String executionDateTime) {
		this.executionDateTime = executionDateTime;
	}


	public BigDecimal getExecutedPrice() {
		return executedPrice;
	}


	public void setExecutedPrice(BigDecimal executedPrice) {
		this.executedPrice = executedPrice;
	}


	public int getExecutedQty() {
		return executedQty;
	}


	public void setExecutedQty(int executedQty) {
		this.executedQty = executedQty;
	}


	public String getBuyBrokerID() {
		return buyBrokerID;
	}


	public void setBuyBrokerID(String buyBrokerID) {
		this.buyBrokerID = buyBrokerID;
	}

	/**
	 * Key for future use
	 */
	public String toString() {
		return (this.executionDateTime + this.stockNo + this.executedPrice + this.executedQty);		
	}
	
}
