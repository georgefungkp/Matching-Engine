package equity.client;

import equity.objectpooling.OrderManager;
import equity.objectpooling.Order;
import equity.objectpooling.Order.Action;
import equity.objectpooling.Order.OrderType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;


/**
 * This class generates random order request objects for demonstration purposes.
 */
public class RandomOrderRequestGenerator {
	private static final Random randomSeed = new Random();


	public static Order getNewLimitOrder(String stockNo, String brokerID, String clientOrdID, String direction, Double price, Integer qty) {

		if (stockNo == null)
			stockNo = String.format("%05d", randomSeed.nextInt(1,3) + 1);
		if (brokerID == null)
			brokerID = String.format("%03d", randomSeed.nextInt(1,10) + 1);
		if (clientOrdID == null)
			clientOrdID = String.format("%05d", randomSeed.nextInt(1,10) + 1);
		Action action;
		if (direction == null)
			action = randomSeed.nextBoolean() ? Action.BUY : Action.SELL;
		else
			action = Action.getByValue(direction);
		if (price == null)
			price = BigDecimal.valueOf(randomSeed.nextDouble(0.1, 0.5) * 100).setScale(2, RoundingMode.HALF_UP).doubleValue();
		if (qty == null)
			qty = randomSeed.nextInt(1,9) * 10;
		OrderType orderType = OrderType.LIMIT;

		return OrderManager.requestOrder(stockNo, brokerID, clientOrdID, orderType, action, price, qty);
	}

	public static Order getNewMarketOrder(String stockNo, String brokerID, String clientOrdID, String direction, Integer qty) {
		if (stockNo == null)
			stockNo = String.format("%05d", randomSeed.nextInt(3) + 1);
		if (brokerID == null)
			brokerID = String.format("%03d", randomSeed.nextInt(10) + 1);
		if (clientOrdID == null)
			clientOrdID = String.format("%05d", randomSeed.nextInt(10) + 1);
		Action action;
		if (direction == null)
			action = randomSeed.nextBoolean() ? Action.BUY : Action.SELL;
		else
			action = Action.getByValue(direction);
		if (qty == null)
			qty = randomSeed.nextInt(1000);
		OrderType orderType = OrderType.MARKET;

		return OrderManager.requestOrder(stockNo, brokerID, clientOrdID, orderType, action, null, qty);
	}

}
