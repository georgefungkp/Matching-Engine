package com.baml.equity.orderprocessing;

import equity.orderprocessing.LimitOrderMatching;
import equity.vo.Order;
import equity.vo.Trade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

public class TestLimitOrderMatching{
	protected TreeMap<BigDecimal, PriorityQueue<Order>> bidMap;
	protected TreeMap<BigDecimal, PriorityQueue<Order>> askMap;
	Comparator<Order> orderCmp;


    @BeforeEach
	protected void setUp() {
		orderCmp = new Comparator<>() {
            /**
             * Earlier order has higher priority
             */
            @Override
            public int compare(Order o1, Order o2) {
                return (o1.getOrderSubmittedTime().compareTo(o2.getOrderSubmittedTime()));
            }
        };
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

		Order bidOrder1 = new Order("Broker_1", "001", 300);
		PriorityQueue<Order> orderList = new PriorityQueue<>(orderCmp);
		orderList.add(bidOrder1);
		bidMap.put(BigDecimal.valueOf(8.1), orderList);
		Order askOrder1 = new Order("Broker_2", "002", 400);
		Order askOrder2 = new Order("Broker_2", "003", 300);
		Order askOrder3 = new Order("Broker_2", "004", 100);

		orderList = new PriorityQueue<>(orderCmp);
		orderList.add(askOrder1);
		orderList.add(askOrder2);
		askMap.put(BigDecimal.valueOf(8.5), orderList);
		orderList = new PriorityQueue<>(orderCmp);
		orderList.add(askOrder3);
		askMap.put(BigDecimal.valueOf(8.2), orderList);

	}

    @Test
	public void testNoNewBid() {
		List<Trade> trades = LimitOrderMatching.orderMatching("00001", bidMap, askMap);
		assertNotNull(trades);
		assertEquals(0, trades.size());
	}


    @Test
	public void testCrossBid() {
		PriorityQueue<Order> orderList = new PriorityQueue<>(orderCmp);
		Order bidOrder2 = new Order("Broker_1", "005", 200);
		orderList.add(bidOrder2);
		bidMap.put(BigDecimal.valueOf(8.5), orderList);
		List<Trade> trades = LimitOrderMatching.orderMatching("00001", bidMap, askMap);
		assertEquals(2, trades.size());
		Trade trade1 = trades.getFirst();
		assertEquals("00001", trade1.getStockNo());
		assertEquals("Broker_1", trade1.getBuyBrokerID());
		assertEquals("Broker_2", trade1.getSellBrokerID());
		assertEquals(100, trade1.getQuantity());
		assertEquals(8.2, trade1.getExecutedPrice().doubleValue());
		Trade trade2 = trades.get(1);
		assertEquals("00001", trade2.getStockNo());
		assertEquals("Broker_1", trade2.getBuyBrokerID());
		assertEquals("Broker_2", trade2.getSellBrokerID());
		assertEquals(100, trade2.getQuantity());
		assertEquals(8.5, trade2.getExecutedPrice().doubleValue());	
		assertEquals(8.1, bidMap.firstKey().doubleValue());
		assertEquals(8.5, askMap.firstKey().doubleValue());
		
	}

    @Test
	public void testBidBookEmpty() {
		bidMap.clear();
		List<Trade> trades = LimitOrderMatching.orderMatching("00001", bidMap, askMap);
		assertNotNull(trades);
		assertEquals(0, trades.size());		
	}

    @Test
	public void testAskBookEmpty() {
		askMap.clear();
		List<Trade> trades = LimitOrderMatching.orderMatching("00001", bidMap, askMap);
		assertNotNull(trades);
		assertEquals(0, trades.size());				
	}

	@Test
	public void testBigBid() {
		PriorityQueue<Order> orderList = new PriorityQueue<>(orderCmp);
		Order bidOrder = new Order("Broker_1", "005", 20000);
		orderList.add(bidOrder);
		bidMap.put(BigDecimal.valueOf(10), orderList);
		List<Trade> trades = LimitOrderMatching.orderMatching("00001", bidMap, askMap);
		assertEquals(3, trades.size());	
		assertTrue(askMap.isEmpty());
		
	}

	@Test
	public void testBigAsk() {
		PriorityQueue<Order> orderList = new PriorityQueue<>(orderCmp);
		Order askOrder = new Order("Broker_1", "005", 20000);
		orderList.add(askOrder);
		askMap.put(BigDecimal.valueOf(7), orderList);
		List<Trade> trades = LimitOrderMatching.orderMatching("00001", bidMap, askMap);
		assertEquals(1, trades.size());	
		assertTrue(bidMap.isEmpty());
		
	}

	@Test
	public void testJustMatch() {
		PriorityQueue<Order> orderList = new PriorityQueue<>(orderCmp);
		Order bidOrder = new Order("Broker_1", "005", 100);
		orderList.add(bidOrder);
		bidMap.put(BigDecimal.valueOf(8.2), orderList);
		List<Trade> trades = LimitOrderMatching.orderMatching("00001", bidMap, askMap);
		assertEquals(1, trades.size());	
		assertEquals(8.5, askMap.firstKey().doubleValue());
		
	}
	
	
}
