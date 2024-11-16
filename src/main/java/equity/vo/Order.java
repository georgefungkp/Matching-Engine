package equity.vo;

import java.sql.Timestamp;
import java.time.Instant;

public class Order {
	private String brokerId;
	private int quantity;
	private Timestamp orderSubmittedTime;
	
	
	public Order (String brokerId, int quantity) {
		this.brokerId = brokerId;
		this.quantity = quantity;
		this.orderSubmittedTime = Timestamp.from(Instant.now());
	}

	public int getQuantity() {
		return quantity;
	}
	public void setQuantity(int quantity) {
		this.quantity = quantity;
	}
	public String getBrokerId() {
		return brokerId;
	}
	public void setBrokerId(String brokerId) {
		this.brokerId = brokerId;
	}
	
	public Timestamp getOrderSubmittedTime() {
		return orderSubmittedTime;
	}

	/**
	 * Key for future use
	 */
	public String toString() {
		return (this.brokerId + this.quantity + this.orderSubmittedTime);
	}
}