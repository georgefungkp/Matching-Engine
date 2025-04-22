package equity.client;

import equity.vo.Order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;
import java.util.Random;

/**
 * This class generates random order request objects for demonstration purposes.
 */
public class RandomOrderRequestGenerator {
	private static final Random randomSeed = new Random();

	public static Order getNewLimitOrder() {
		String stockNo = String.format("%05d", randomSeed.nextInt(3) + 1);
		String brokerId = String.format("%03d", randomSeed.nextInt(10) + 1);
		String clientOrdId = String.format("%05d", randomSeed.nextInt(10) + 1);
		String direction = randomSeed.nextBoolean() ? "B" : "S";
		String orderType = "L";
		Double price = BigDecimal.valueOf(randomSeed.nextDouble() * 100).setScale(2, RoundingMode.HALF_UP).doubleValue();
		int quantity = randomSeed.nextInt(1000);
		ZonedDateTime createTime = ZonedDateTime.now();
		ZonedDateTime eventTime = ZonedDateTime.now();

		return new Order(stockNo, brokerId, clientOrdId, orderType, direction, price, quantity, createTime, eventTime);
	}

	public static Order getNewMarketOrder() {
		String stockNo = String.format("%05d", randomSeed.nextInt(3) + 1);
		String brokerId = String.format("%03d", randomSeed.nextInt(10) + 1);
		String clientOrdId = String.format("%05d", randomSeed.nextInt(10) + 1);
		String direction = randomSeed.nextBoolean() ? "B" : "S";
		String orderType = "M";
		Double price = BigDecimal.valueOf(randomSeed.nextDouble() * 100).setScale(2, RoundingMode.HALF_UP).doubleValue();
		int quantity = randomSeed.nextInt(1000);
		ZonedDateTime createTime = ZonedDateTime.now();
		ZonedDateTime eventTime = ZonedDateTime.now();

		return new Order(stockNo, brokerId, clientOrdId, orderType, direction, price, quantity, createTime, eventTime);
	}

}
