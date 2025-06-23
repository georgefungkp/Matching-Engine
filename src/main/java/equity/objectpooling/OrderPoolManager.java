package equity.objectpooling;

import equity.objectpooling.Order.OrderType;
import equity.objectpooling.Order.Side;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static util.ReadConfig.getStocks;

/**
 * A class responsible for managing orders through an object pool mechanism.
 */
public class OrderPoolManager {

    private static final Map<String, OrderObjectPool> mainOrderObjMap = new ConcurrentHashMap<>();
    private static final Map<String, TradeObjectPool> mainTradeObjMap = new ConcurrentHashMap<>();

    static {
        for (String stockId: getStocks()) {
            mainOrderObjMap.put(stockId, new OrderObjectPool(stockId));
            mainTradeObjMap.put(stockId, new TradeObjectPool(stockId));
        }
    }

    public static Order requestOrderObj(String stockNo, String brokerID, String clientOrdID, OrderType orderType, Side buyOrSell, BigDecimal price, int quantity){
        OrderObjectPool pool = mainOrderObjMap.get(stockNo);
        if (pool == null) {
            throw new IllegalArgumentException("Invalid stock number: " + stockNo + " No order object pool exists for this stock.");
        }
        return pool.makeANewOrder(brokerID, clientOrdID, orderType, buyOrSell, price, quantity);
    }

    public static Trade requestTradeObj(Order bidOrder, Order askOrder, String stockNo, BigDecimal executedPrice, int executedQty, String executionDateTime){
        TradeObjectPool pool = mainTradeObjMap.get(stockNo);
        if (pool == null) {
            throw new IllegalArgumentException("Invalid stock number: " + stockNo + " No trade object pool exists for this stock.");
        }
        return pool.makeANewTrade(bidOrder, askOrder, stockNo, executedPrice, executedQty, executionDateTime);
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

    public static void clearObjects(String stockNo) {
        OrderObjectPool orderPool = mainOrderObjMap.get(stockNo);
        TradeObjectPool tradePool = mainTradeObjMap.get(stockNo);

        if (orderPool != null) {
            orderPool.resetPool();
        }else{
            throw new IllegalArgumentException("Invalid stock number: " + stockNo + " No order object pool exists for this stock.");
        }
        if (tradePool != null) {
            tradePool.resetPool();
        }else{
            throw new IllegalArgumentException("Invalid stock number: " + stockNo + " No trade object pool exists for this stock.");
        }
    }

}

