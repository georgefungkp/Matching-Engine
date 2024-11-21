package equity.vo;

import equity.externalparties.MarketDataJob;
import equity.orderprocessing.LimitOrderMatching;
import equity.orderprocessing.MarketOrderMatching;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.PriorityQueue;
import java.util.TreeMap;

/*
 * Each stock has 2 order books
 */
public class OrderBooksForStock {
    private static final Logger log = LogManager.getLogger(MarketDataJob.class);
    //	private BigDecimal bestBid;
//	private BigDecimal bestAsk;
    // Bid order book
    TreeMap<BigDecimal, PriorityQueue<Order>> bidMap;
    // Ask order book
    TreeMap<BigDecimal, PriorityQueue<Order>> askMap;
    private final String stockNo;
    private final String desc;
    private BigDecimal nominalPrice;

    public OrderBooksForStock(String stockNo, BigDecimal nominalPrice, String desc) {
        log.debug("Creating order books of " + desc);
        this.stockNo = stockNo;
        this.nominalPrice = nominalPrice;
        this.desc = desc;
        bidMap = new TreeMap<>(new Comparator<>() {
            /**
             * Lower bid price has higher priority
             */
            @Override
            public int compare(BigDecimal o1, BigDecimal o2) {
                return o1.compareTo(o2);
            }
        });
        askMap = new TreeMap<>(new Comparator<>() {
            /**
             * Higher ask price has higher priority
             */
            @Override
            public int compare(BigDecimal o1, BigDecimal o2) {
                return o2.compareTo(o1);
            }
        });
    }

    public String getStockNo() {
        return stockNo;
    }

    public BigDecimal getNominalPrice() {
        return nominalPrice;
    }

    public void setNominalPrice(BigDecimal nominalPrice) {
        this.nominalPrice = nominalPrice;
    }

    public BigDecimal getBestBid() {
        if (!bidMap.isEmpty()) {
            return bidMap.lastKey();
        } else {
            return null;
        }

    }

    public BigDecimal getBestAsk() {
        if (!askMap.isEmpty()) {
            return askMap.lastKey();
        } else {
            return null;
        }
    }

    public TreeMap<BigDecimal, PriorityQueue<Order>> getBidMap() {
        return bidMap;
    }

    public TreeMap<BigDecimal, PriorityQueue<Order>> getAskMap() {
        return askMap;
    }

    /**
     * insert order into the bid book and try to match order
     */
    public synchronized List<Trade> addBid(OrderRequest orderReq) {
        TreeMap<BigDecimal, PriorityQueue<Order>> orderMap;
        if ("B".equals(orderReq.buyOrSell())) {
            orderMap = bidMap;
        } else {
            orderMap = askMap;
        }

        if (!orderMap.containsKey(orderReq.price())) {
            PriorityQueue<Order> orderList = new PriorityQueue<>(new Comparator<>() {
                /**
                 * Earlier order has higher priority
                 */
                @Override
                public int compare(Order o1, Order o2) {
                    return (o1.getOrderSubmittedTime().compareTo(o2.getOrderSubmittedTime()));
                }
            });

            orderList.add(new Order(orderReq.brokerId(), orderReq.quantity()));

            if ("M".equals(orderReq.orderType())) {
                if ("B".equals(orderReq.buyOrSell())) {
                    orderMap.put(BigDecimal.valueOf(Integer.MAX_VALUE), orderList);
                } else {
                    orderMap.put(BigDecimal.valueOf(0), orderList);
                }
            } else if ("L".equals(orderReq.orderType())) {
                orderMap.put(orderReq.price(), orderList);
            }
        } else {
            PriorityQueue<Order> orderList = orderMap.get(orderReq.price());
            orderList.add(new Order(orderReq.brokerId(), orderReq.quantity()));
        }
        showMap(orderMap, orderReq.buyOrSell());

//		return LimitOrderMatching.orderMatching(orderReq.getStockNo(), bidMap, askMap);
        synchronized (this) {
            return switch (orderReq.orderType()) {
                case "L" -> LimitOrderMatching.orderMatching(orderReq.stockNo(), bidMap, askMap);
                case "M" -> MarketOrderMatching.orderMatching(orderReq.stockNo(), this.nominalPrice, bidMap, askMap);
                default -> null;
            };
        }

    }

    public void showMap(TreeMap<BigDecimal, PriorityQueue<Order>> orderMap, String buyOrSell) {
        log.debug("{}_{} ", this.desc, buyOrSell);
        log.debug("the highest priority price: {}", orderMap.lastKey());
        log.debug("the lowest priority price: {}", orderMap.firstKey());
        for (Entry<BigDecimal, PriorityQueue<Order>> entry : orderMap.entrySet()) {
            log.debug(entry.getKey());
            for (Order order : entry.getValue()) {
                System.out.print(order.getBrokerId() + "," + order.getQuantity() + ","
                        + order.getOrderSubmittedTime().toLocalDateTime() + " ");
            }
            log.debug("The time of head is {}", entry.getValue().peek().getOrderSubmittedTime());
        }

    }

}
