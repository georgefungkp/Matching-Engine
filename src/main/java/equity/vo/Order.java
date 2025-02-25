package equity.vo;

import java.sql.Timestamp;
import java.time.Instant;

public class Order {
	private final String brokerId;
	private final String clientOrderId;
	private int quantity;
	private final Timestamp orderSubmittedTime;
	
	
	public Order (String brokerId, String brokerOrdId, int quantity) {
		this.brokerId = brokerId;
		this.clientOrderId = brokerOrdId;
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
	public String getClientOrderId() {
		return clientOrderId;
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