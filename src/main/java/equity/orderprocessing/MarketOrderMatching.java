package equity.orderprocessing;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeMap;

import equity.vo.Order;
import equity.vo.Trade;

public class MarketOrderMatching {

	
	public static BigDecimal getPrice(Map.Entry<BigDecimal, PriorityQueue<Order>> lastAskEntry, Map.Entry<BigDecimal, PriorityQueue<Order>> lastBidEntry, BigDecimal nominalPrice) {
		BigDecimal buyPrice = lastAskEntry.getKey();
		if (lastAskEntry.getKey().equals(BigDecimal.valueOf(0)) && lastBidEntry.getKey().equals(BigDecimal.valueOf(Integer.MAX_VALUE))){
			buyPrice = nominalPrice;
		}else if (lastAskEntry.getKey().equals(BigDecimal.valueOf(0))) {
			buyPrice = lastBidEntry.getKey();
		}
		return buyPrice;
	}
	
	public static List<Trade> orderMatching(String stockNo, BigDecimal nominalPrice, TreeMap<BigDecimal, PriorityQueue<Order>> bidMap, TreeMap<BigDecimal, PriorityQueue<Order>> askMap) {
		List<Trade> tradeList = new ArrayList<Trade>();
		if (!askMap.isEmpty() && !bidMap.isEmpty()) {
			while (askMap.lastKey().compareTo(bidMap.lastKey()) <= 0) {
				Map.Entry<BigDecimal, PriorityQueue<Order>> lastAskEntry = askMap.lastEntry();
				Map.Entry<BigDecimal, PriorityQueue<Order>> lastBidEntry = bidMap.lastEntry();
				PriorityQueue<Order> askQueue = lastAskEntry.getValue();
				while (!askQueue.isEmpty()) {
					Order askOrder = askQueue.peek();
					int sellQty = askOrder.getQuantity();
					PriorityQueue<Order> bidQueue = lastBidEntry.getValue();
					while (!bidQueue.isEmpty()) {
						Order bidOrder = bidQueue.peek();
						if (sellQty >= bidOrder.getQuantity()) {
							// Fully fulfill bid order
							tradeList.add(new Trade(bidOrder.getBrokerId(), askOrder.getBrokerId(), stockNo,
									getPrice(lastAskEntry, lastBidEntry, nominalPrice), bidOrder.getQuantity(), LocalDateTime.now().toString()));
							sellQty -= bidOrder.getQuantity();
							bidQueue.remove();
							if (sellQty == 0) {
								// Fully fulfill ask order
								askQueue.remove();
								if (askQueue.isEmpty()) {
									// No outstanding ask order
									askMap.pollLastEntry();
									break;
								}								
								break;
							}else {
								// Partial fulfill ask order
								askOrder.setQuantity(sellQty);
							}
						} else {
							// Partial fulfill bid order
							tradeList.add(new Trade(bidOrder.getBrokerId(), askOrder.getBrokerId(), stockNo,
									getPrice(lastAskEntry, lastBidEntry, nominalPrice), sellQty, LocalDateTime.now().toString()));
							bidOrder.setQuantity(bidOrder.getQuantity() - sellQty);
							// Fully fulfill ask order
							sellQty = 0;
							askQueue.remove();
							if (askQueue.isEmpty()) {
								// No outstanding ask order
								askMap.pollLastEntry();
								break;
							}							
							break;
						}
					}
					if (bidQueue.isEmpty()) {
						// No outstanding bid order
						bidMap.pollLastEntry();
						break;
					}

				}
				if (askMap.isEmpty() || bidMap.isEmpty()) {
					break;
				}
			}
		}

		return tradeList;


	}
}
