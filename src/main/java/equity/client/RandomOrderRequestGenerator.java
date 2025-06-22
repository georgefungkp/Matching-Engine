package equity.client;

import equity.objectpooling.OrderPoolManager;
import equity.objectpooling.Order;
import equity.objectpooling.Order.Side;
import equity.objectpooling.Order.OrderType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;


/**
 * This class generates random order request objects for demonstration purposes.
 */
public class RandomOrderRequestGenerator {
	private static final Random randomSeed = new Random();


	public static Order getNewLimitOrder(String stockNo, String brokerID, String clientOrdID, String direction, BigDecimal price, Integer qty) {

		if (stockNo == null)
			stockNo = String.format("%05d", randomSeed.nextInt(1,3) + 1);
		if (brokerID == null)
			brokerID = String.format("%03d", randomSeed.nextInt(1,10) + 1);
		if (clientOrdID == null)
			clientOrdID = String.format("%05d", randomSeed.nextInt(1,10) + 1);
		Side side;
		if (direction == null)
			side = randomSeed.nextBoolean() ? Side.BUY : Side.SELL;
		else
			side = Side.getByValue(direction);
		
		if (price == null) {
			double randomPrice = randomSeed.nextDouble(0.1, 0.5) * 100;
			price = BigDecimal.valueOf(randomPrice).setScale(2, RoundingMode.HALF_UP);
		}

		if (qty == null)
			qty = randomSeed.nextInt(1,9) * 10;
		OrderType orderType = OrderType.LIMIT;

		return OrderPoolManager.requestOrderObj(stockNo, brokerID, clientOrdID, orderType, side, price, qty);
	}

	public static Order getNewMarketOrder(String stockNo, String brokerID, String clientOrdID, String direction, Integer qty) {
		if (stockNo == null)
			stockNo = String.format("%05d", randomSeed.nextInt(3) + 1);
		if (brokerID == null)
			brokerID = String.format("%03d", randomSeed.nextInt(10) + 1);
		if (clientOrdID == null)
			clientOrdID = String.format("%05d", randomSeed.nextInt(10) + 1);
		Side side;
		if (direction == null)
			side = randomSeed.nextBoolean() ? Side.BUY : Side.SELL;
		else
			side = Side.getByValue(direction);
		if (qty == null)
			qty = randomSeed.nextInt(1000);
		OrderType orderType = OrderType.MARKET;

		return OrderPoolManager.requestOrderObj(stockNo, brokerID, clientOrdID, orderType, side, null, qty);
	}

}
