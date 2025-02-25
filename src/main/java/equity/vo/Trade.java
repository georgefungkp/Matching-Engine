package equity.vo;

import java.math.BigDecimal;

public class Trade {
	String buyBrokerID;
	String sellBrokerID;
	String buyOrderID;
	String sellOrderID;
	String stockNo;
	BigDecimal executedPrice;
	int executedQty;
	String executionDateTime;
	
	public Trade(String buyBrokerID, String sellBrokerID, String buyOrderID, String sellOrderID,
				 String stockNo, BigDecimal executedPrice, int executedQty, String executionDateTime) {
		this.buyBrokerID = buyBrokerID;
		this.sellBrokerID = sellBrokerID;
		this.buyOrderID = buyOrderID;
		this.sellOrderID = sellOrderID;
		this.stockNo = stockNo;
		this.executedPrice = executedPrice;
		this.executedQty = executedQty;
		this.executionDateTime = executionDateTime;
	}
	
	
	public String getSellBrokerID() {
		return sellBrokerID;
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

	public String getExecutionDateTime() {
		return executionDateTime;
	}


	public BigDecimal getExecutedPrice() {
		return executedPrice;
	}


	public int getExecutedQty() {
		return executedQty;
	}


	public String getBuyBrokerID() {
		return buyBrokerID;
	}


	/**
	 * Key for future use
	 */
	public String toString() {
		return (this.executionDateTime + this.stockNo + this.executedPrice + this.executedQty);		
	}
	
}
