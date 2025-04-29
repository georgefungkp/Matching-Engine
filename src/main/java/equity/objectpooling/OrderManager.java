package equity.objectpooling;

import equity.objectpooling.Order.Action;
import equity.objectpooling.Order.OrderType;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static util.ReadConfig.dotenv;

/**
 * A class responsible for managing orders through an object pool mechanism.
 */
public class OrderManager {

    private static final Map<String, OrderObjectPool> mainOrderMap = new ConcurrentHashMap<>();

    static {
        int noOfStock = Integer.parseInt(Objects.requireNonNull(dotenv.get("no_of_stock")));
        for (int i = 0; i < noOfStock; i++) {
            mainOrderMap.put(String.format("%05d", i), new OrderObjectPool(String.format("%05d", i)));
        }
    }

    public static Order requestOrder(String stockNo, String brokerID, String clientOrdID, OrderType orderType, Action buyOrSell, Double price, int quantity){
        return mainOrderMap.get(stockNo).makeANewOrder(brokerID, clientOrdID, orderType, buyOrSell, price, quantity);
    }

    // Accept an object back to pool
    public static void returnOrder(Order order) {
        mainOrderMap.get(order.getStockNo()).returnOrder(order);
    }

    public static void returnOrders(Order... orders){
        for (Order order: orders)
            mainOrderMap.get(order.getStockNo()).returnOrder(order);
    }

    public static int getFreeOrderCount(String stockNo){
        return mainOrderMap.get(stockNo).getFreeOrdersCount();
    }

    public static int getUsedOrderCount(String stockNo){
        return mainOrderMap.get(stockNo).getUsedOrdersCount();
    }

    public static void clearOrderObjects(String stockNo){
        mainOrderMap.get(stockNo).resetPool();
    }
}

