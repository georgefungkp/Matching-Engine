package equity.objectpooling;

import equity.objectpooling.Order.Action;
import equity.objectpooling.Order.OrderType;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static util.ReadConfig.dotenv;

/**
 * A class responsible for managing orders through an object pool mechanism.
 */
public class OrderPoolManager {

    private static final Map<String, OrderObjectPool> mainOrderObjMap = new ConcurrentHashMap<>();
    private static final Map<String, TradeObjectPool> mainTradeObjMap = new ConcurrentHashMap<>();

    static {
        int noOfStock = Integer.parseInt(Objects.requireNonNull(dotenv.get("no_of_stock")));
        for (int i = 0; i < noOfStock; i++) {
            mainOrderObjMap.put(String.format("%05d", i), new OrderObjectPool(String.format("%05d", i)));
            mainTradeObjMap.put(String.format("%05d", i), new TradeObjectPool(String.format("%05d", i)));
        }
    }

    public static Order requestOrderObj(String stockNo, String brokerID, String clientOrdID, OrderType orderType, Action buyOrSell, Double price, int quantity){
        return mainOrderObjMap.get(stockNo).makeANewOrder(brokerID, clientOrdID, orderType, buyOrSell, price, quantity);
    }

    public static Trade requestTradeObj(String buyBrokerID, String sellBrokerID, String buyOrderID, String sellOrderID, String stockNo, Double executedPrice, int executedQty, String executionDateTime){
        return mainTradeObjMap.get(stockNo).makeANewTrade(buyBrokerID, sellBrokerID, buyOrderID, sellOrderID,
                stockNo, executedPrice, executedQty, executionDateTime);
    }

    // Accept an object back to pool
    public static void returnOrderObj(@NotNull Order order) {
        mainOrderObjMap.get(order.getStockNo()).returnOrderObj(order);
    }

    public static void returnTradeObj(@NotNull Trade trade) {
        mainTradeObjMap.get(trade.getStockNo()).returnTradeObj(trade);
    }

    public static void returnOrders(@NotNull Order... orders){
        for (Order order: orders)
            mainOrderObjMap.get(order.getStockNo()).returnOrderObj(order);
    }

    public static int getFreeOrderCount(String stockNo){
        return mainOrderObjMap.get(stockNo).getFreeOrderCount();
    }

    public static int getUsedOrderCount(String stockNo){
        return mainOrderObjMap.get(stockNo).getUsedOrderCount();
    }

    public static int getFreeTradeCount(String stockNo){
        return mainTradeObjMap.get(stockNo).getFreeTradeCount();
    }

    public static int getUsedTradeCount(String stockNo){
        return mainTradeObjMap.get(stockNo).getUsedTradeCount();
    }

    public static void clearObjects(String stockNo){
        mainOrderObjMap.get(stockNo).resetPool();
        mainTradeObjMap.get(stockNo).resetPool();
    }
}

