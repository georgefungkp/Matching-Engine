package com.baml.equity.orderprocessing;

import equity.client.RandomOrderRequestGenerator;
import equity.objectpooling.*;
import equity.orderprocessing.LimitOrderMatchingJob;
import equity.orderprocessing.OrderProcessingJob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Spy;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestObjectPool {
    private static final ConcurrentHashMap<String, Order> orderObjMapper = new ConcurrentHashMap<>();
    @Spy
    private final LinkedBlockingQueue<Order> orderQueue = new LinkedBlockingQueue<>();
	@Spy
	private LinkedBlockingQueue<MarketData> marketDataQueue = new LinkedBlockingQueue<>();
	@Spy
	private LinkedBlockingQueue<Trade>	tradeDataQueue = new LinkedBlockingQueue<>();

    OrderProcessingJob orderProcessingJob;
    LimitOrderMatchingJob orderMatching;
	int noOfStocks = 2;
	Map<String, OrderBook> orderBooks = new HashMap<>();

    @BeforeEach
	protected void setUp() {
		for (int i=1; i<=noOfStocks; i++ ) {
			orderBooks.put(String.format("%05d", i), new OrderBook(String.format("%05d", i), "Stock " + 1));
		}
        orderProcessingJob = new OrderProcessingJob(orderQueue, orderBooks, orderObjMapper);
        OrderBook orderBook = orderBooks.get("00001");
        orderMatching = new LimitOrderMatchingJob(orderBook, orderObjMapper, marketDataQueue, tradeDataQueue);
		Order bidOrder1 = RandomOrderRequestGenerator.getNewLimitOrder("00001", "Broker 1",
				"001", "B", 8.1, 300);
        orderProcessingJob.putOrder(bidOrder1);

		Order askOrder1 = RandomOrderRequestGenerator.getNewLimitOrder("00001", "Broker 2",
				"002", "S", 8.1, 300);
        orderProcessingJob.putOrder(askOrder1);
		assertEquals(0, OrderManager.getFreeOrderCount("00001"));
		assertEquals(2, OrderManager.getUsedOrderCount("00001"));
    }

	/**
	 * Clears data structures and objects related to order processing, market data, and trade data after each test.
	 */
	@AfterEach
	public void tearDown(){
		orderObjMapper.clear();
		marketDataQueue.clear();
        tradeDataQueue.clear();
		OrderManager.clearObjects("00001");
		OrderManager.clearObjects("00002");
	}

    @Test
    public void testReUseOrderObject() throws InterruptedException {
		assertEquals(0, OrderManager.getFreeTradeCount("00001"));
		assertEquals(0, OrderManager.getUsedTradeCount("00001"));
        orderMatching.matchTopOrder();
		assertEquals(2, OrderManager.getFreeOrderCount("00001"));
		assertEquals(0, OrderManager.getUsedOrderCount("00001"));
		assertEquals(0, OrderManager.getFreeTradeCount("00001"));
		assertEquals(1, OrderManager.getUsedTradeCount("00001"));

		Order askOrder1 = RandomOrderRequestGenerator.getNewLimitOrder("00001", "Broker 2",
				"001", "S", 8.0, 100);
        orderProcessingJob.putOrder(askOrder1);
		assertEquals(1, OrderManager.getFreeOrderCount("00001"));
		assertEquals(1, OrderManager.getUsedOrderCount("00001"));

		// stock 00002
		Order askOrder2 = RandomOrderRequestGenerator.getNewLimitOrder("00002", "Broker 2",
				"001", "S", 8.0, 100);
		assertEquals(1, OrderManager.getFreeOrderCount("00001"));
		assertEquals(1, OrderManager.getUsedOrderCount("00001"));
		assertEquals(0, OrderManager.getFreeOrderCount("00002"));
		assertEquals(1, OrderManager.getUsedOrderCount("00002"));
		assertEquals(0, OrderManager.getFreeTradeCount("00001"));
		assertEquals(1, OrderManager.getUsedTradeCount("00001"));
		assertEquals(0, OrderManager.getFreeTradeCount("00002"));
		assertEquals(0, OrderManager.getUsedTradeCount("00002"));
    }

	@Test
  	public void testReUseTradeObject() throws InterruptedException {
		orderMatching.matchTopOrder();
		assertEquals(0, OrderManager.getFreeTradeCount("00001"));
		assertEquals(1, OrderManager.getUsedTradeCount("00001"));


	}
}
