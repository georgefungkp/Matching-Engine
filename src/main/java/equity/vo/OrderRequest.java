package equity.vo;

import java.math.BigDecimal;

public class OrderRequest {
	private String stockNo;
	private String brokerId;
	private String orderType;
	private String buyOrSell;
	private BigDecimal price;
	private int quantity;
	
	
	public OrderRequest (String stockNo, String brokerId, String orderType, String buyOrSell, BigDecimal price, int quantity) {
		this.stockNo = stockNo;
		this.brokerId = brokerId;
		this.orderType = orderType;
		this.buyOrSell = buyOrSell;
		this.price = price;
		this.quantity = quantity;
	}

	public int getQuantity() {
		return quantity;
	}
	public void setQuantity(int quantity) {
		this.quantity = quantity;
	}

	public String getStockNo() {
		return stockNo;
	}

	public void setStockNo(String stockNo) {
		this.stockNo = stockNo;
	}

	public String getBrokerId() {
		return brokerId;
	}

	public void setBrokerId(String brokerId) {
		this.brokerId = brokerId;
	}

	public String getBuyOrSell() {
		return buyOrSell;
	}

	public void setBuyOrSell(String buyOrSell) {
		this.buyOrSell = buyOrSell;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public void setPrice(BigDecimal price) {
		this.price = price;
	}

	public String getOrderType() {
		return orderType;
	}

	public void setOrderType(String orderType) {
		this.orderType = orderType;
	}

	/**
	 * Key for future use
	 */
	public String toString() {
		return (this.stockNo + this.brokerId + this.buyOrSell + this.quantity); 
	}
	
}
