package equity.client;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;

public class RandomOrderRequest {
	private static final Random randomSeed = new Random();
	private String brokerId;


	private String clientOrdId;
	private final String stockNo;
	private final char direction;
	private final char orderType;
	private final BigDecimal price;
	private final int quantity;

	public RandomOrderRequest() {
		this.stockNo = String.format("%05d", randomSeed.nextInt(3) + 1);
		this.brokerId = String.format("%03d", randomSeed.nextInt(3) + 1);
		this.clientOrdId = String.format("%05d", randomSeed.nextInt(10) + 1);
		this.direction = randomSeed.nextBoolean() ? 'B' : 'S';
		this.orderType = randomSeed.nextBoolean() ? 'M' : 'L';
		this.price = BigDecimal.valueOf(randomSeed.nextDouble() * 100).setScale(2, RoundingMode.HALF_UP);
		this.quantity = randomSeed.nextInt(1000);

	}

	public String getStockNo() {
		return stockNo;
	}

	public char getDirection() {
		return direction;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public int getQuantity() {
		return quantity;
	}

	public char getOrderType() {
		return orderType;
	}

	public String getBrokerId() {
		return brokerId;
	}

	public String getClientOrdId() {
		return clientOrdId;
	}


}
