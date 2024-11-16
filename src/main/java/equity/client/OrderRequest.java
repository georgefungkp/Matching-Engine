package equity.client;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;

public class OrderRequest {
	private static Random stockSeed = new Random();
	private String brokerId;
	private String stockNo;
	private char direction;
	private char orderType;
	private BigDecimal price;
	private int quantity;

	public OrderRequest() {
		this.stockNo = String.format("%05d", stockSeed.nextInt(3) + 1);
		this.brokerId = String.format("%03d", stockSeed.nextInt(3) + 1);
		this.direction = stockSeed.nextBoolean() ? 'B' : 'S';
		this.orderType = stockSeed.nextBoolean() ? 'M' : 'L';
		this.price = new BigDecimal(stockSeed.nextDouble() * 100).setScale(2, RoundingMode.HALF_UP);
		this.quantity = stockSeed.nextInt(1000);

	}

	public static Random getStockSeed() {
		return stockSeed;
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

	public void setBrokerId(String brokerId) {
		this.brokerId = brokerId;
	}

}
