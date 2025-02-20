package equity.orderprocessing;

import equity.vo.Order;
import equity.vo.Trade;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public class LimitOrderMatching {

	public static List<Trade> orderMatching(String stockNo,
                                            TreeMap<BigDecimal, PriorityQueue<Order>> bidMap, TreeMap<BigDecimal, PriorityQueue<Order>> askMap) {
		List<Trade> tradeList = new ArrayList<>();
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
									lastAskEntry.getKey(), bidOrder.getQuantity(), LocalDateTime.now().toString()));
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
									lastAskEntry.getKey(), sellQty, LocalDateTime.now().toString()));
							bidOrder.setQuantity(bidOrder.getQuantity() - sellQty);
							// Fully fulfill ask order
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
