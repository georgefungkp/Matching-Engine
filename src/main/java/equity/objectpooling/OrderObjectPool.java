package equity.objectpooling;

import equity.objectpooling.Order.OrderType;
import equity.objectpooling.Order.Side;
import equity.requesthandling.MatchingEngine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public class OrderObjectPool {
    private static final Logger log = LogManager.getLogger(MatchingEngine.class);
    private final Set<Order> inUsedOrderList = ConcurrentHashMap.newKeySet();
    private final Set<Order> freeOrderList = ConcurrentHashMap.newKeySet();
    private final String stockNo;

    public OrderObjectPool(String stockNo) {
        this.stockNo = stockNo;
    }


    /**
     * Construct a new order based on the given parameters.
     * If there is any free object, it will be used. Else, create a new one and adds it to the order object pool.
     *
     * @param brokerID the ID of the broker associated with the order
     * @param clientOrdID the client order ID
     * @param orderType the type of the order (MARKET or LIMIT)
     * @param direction the direction of the order (BUY or SELL)
     * @param price the price of the order
     * @param quantity the quantity of the order
     * @return the Order object
     */
    public synchronized Order makeANewOrder(String brokerID, String clientOrdID, OrderType orderType, Side direction, BigDecimal price, int quantity){
        Order newOrder;
        if (freeOrderList.isEmpty()){
            newOrder = new Order(stockNo, brokerID, clientOrdID, orderType, direction, price, quantity);
        }else{
            Iterator<Order> iterator = freeOrderList.iterator();
            newOrder = iterator.next();
            newOrder.reset(brokerID, clientOrdID, orderType, direction, price, quantity);
            freeOrderList.remove(newOrder);
        }
        inUsedOrderList.add(newOrder);
        return newOrder;
    }


    /**
     * Free up the given Order object back to the free order list in the pool.
     *
     * @param order the Order object to be returned to the free order list
     */
    public synchronized void returnOrderObj(Order order){
        if (order != null){
            if (!inUsedOrderList.remove(order))
                log.error("{} is not in use. Need to check ", order.toString());
            else
                freeOrderList.add(order);
        }
    }

    public int getFreeOrderCount() {
        return freeOrderList.size();
    }

    public int getUsedOrderCount(){
        return inUsedOrderList.size();
    }

    public void resetPool(){
        inUsedOrderList.clear();
        freeOrderList.clear();
    }
}