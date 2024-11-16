package equity.vo;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.PriorityQueue;
import java.util.TreeMap;

import equity.orderprocessing.LimitOrderMatching;
import equity.orderprocessing.MarketOrderMatching;

/*
 * Each stock has 2 order books
 */
public class OrderBooksForStock {
	String stockNo;
	String desc;
	BigDecimal nominalPrice;
	BigDecimal bestBid;
	BigDecimal bestAsk;
	// Bid order book
	TreeMap<BigDecimal, PriorityQueue<Order>> bidMap;
	// Ask order book
	TreeMap<BigDecimal, PriorityQueue<Order>> askMap;

	public OrderBooksForStock(String stockNo, BigDecimal nominalPrice, String desc) {
		System.out.println("Creating order books of " + desc);
		this.stockNo = stockNo;
		this.nominalPrice = nominalPrice;
		this.desc = desc;
		bidMap = new TreeMap<BigDecimal, PriorityQueue<Order>>(new Comparator<BigDecimal>() {
			/**
			 * Lower bid price has higher priority
			 */
			@Override
			public int compare(BigDecimal o1, BigDecimal o2) {
				return o1.compareTo(o2);
			}
		});
		askMap = new TreeMap<BigDecimal, PriorityQueue<Order>>(new Comparator<BigDecimal>() {
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

	public String getDesc() {
		return desc;
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
	 * 
	 * @param stockNo
	 * @param price
	 * @param quantity
	 */
	public synchronized List<Trade> addBid(OrderRequest orderReq) {
		TreeMap<BigDecimal, PriorityQueue<Order>> orderMap;
		if ("B".equals(orderReq.getBuyOrSell())) {
			orderMap = bidMap;
		} else {
			orderMap = askMap;
		}

		if (!orderMap.containsKey(orderReq.getPrice())) {
			PriorityQueue<Order> orderList = new PriorityQueue<Order>(new Comparator<Order>() {
				/**
				 * Earlier order has higher priority
				 */
				@Override
				public int compare(Order o1, Order o2) {
					return (o1.getOrderSubmittedTime().compareTo(o2.getOrderSubmittedTime()));
				}
			});

			orderList.add(new Order(orderReq.getBrokerId(), orderReq.getQuantity()));

			if ("M".equals(orderReq.getOrderType())) {
				if ("B".equals(orderReq.getBuyOrSell())) {
					orderMap.put(BigDecimal.valueOf(Integer.MAX_VALUE), orderList);
				} else {
					orderMap.put(BigDecimal.valueOf(0), orderList);
				}
			} else if ("L".equals(orderReq.getOrderType())) {
				orderMap.put(orderReq.getPrice(), orderList);
			}
		} else {
			PriorityQueue<Order> orderList = orderMap.get(orderReq.getPrice());
			orderList.add(new Order(orderReq.getBrokerId(), orderReq.getQuantity()));
		}
		showMap(orderMap, orderReq.getBuyOrSell());

//		return LimitOrderMatching.orderMatching(orderReq.getStockNo(), bidMap, askMap);
		synchronized (this) {
			switch (orderReq.getOrderType()) {
			case "L":
				return LimitOrderMatching.orderMatching(orderReq.getStockNo(), bidMap, askMap);
			case "M":
				return MarketOrderMatching.orderMatching(orderReq.getStockNo(), this.nominalPrice, bidMap, askMap);
			default:
				return null;
			}
		}

	}

	public void showMap(TreeMap<BigDecimal, PriorityQueue<Order>> orderMap, String buyOrSell) {
		System.out.println(this.desc + "_" + buyOrSell + " ");
		System.out.println("the highest priority price: " + orderMap.lastKey());
		System.out.println("the lowest priority price: " + orderMap.firstKey());
		for (Entry<BigDecimal, PriorityQueue<Order>> entry : orderMap.entrySet()) {
			System.out.println(entry.getKey());
			for (Order order : entry.getValue()) {
				System.out.print(order.getBrokerId() + "," + order.getQuantity() + ","
						+ order.getOrderSubmittedTime().toLocalDateTime() + " ");
			}
			System.out.println();
			System.out.println("The time of head is " + entry.getValue().peek().getOrderSubmittedTime());
		}

	}

}
