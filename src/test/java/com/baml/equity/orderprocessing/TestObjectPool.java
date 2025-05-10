package com.baml.equity.orderprocessing;

import equity.client.RandomOrderRequestGenerator;
import equity.externalparties.ResultingTradeJob;
import equity.fix.server.FIXTradeServerApp;
import equity.objectpooling.*;
import equity.orderprocessing.LimitOrderMatchingJob;
import equity.orderprocessing.OrderProcessingJob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import util.FileChannelService;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class TestObjectPool {
    private static final ConcurrentHashMap<String, Order> orderObjMapper = new ConcurrentHashMap<>();
    private final LinkedBlockingQueue<Order> orderQueue = new LinkedBlockingQueue<>();
	private final LinkedBlockingQueue<MarketData> marketDataQueue = new LinkedBlockingQueue<>();
	private final LinkedBlockingQueue<Trade> tradeDataQueue = new LinkedBlockingQueue<>();
	@Mock
	private FIXTradeServerApp fixTradeServerApp;
	@Mock
	FileChannelService fileChannelService;


	@InjectMocks
	private ResultingTradeJob resultingTradeJob;

    OrderProcessingJob orderProcessingJob;
    LimitOrderMatchingJob orderMatching;
	int noOfStocks = 2;
	Map<String, OrderBook> orderBooks = new HashMap<>();

    @BeforeEach
	protected void setUp() {
		MockitoAnnotations.openMocks(this);
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
		assertEquals(0, OrderPoolManager.getFreeOrderCount("00001"));
		assertEquals(2, OrderPoolManager.getUsedOrderCount("00001"));
    }

	/**
	 * Clears data structures and objects related to order processing, market data, and trade data after each test.
	 */
	@AfterEach
	public void tearDown(){
		orderObjMapper.clear();
		marketDataQueue.clear();
        tradeDataQueue.clear();
		OrderPoolManager.clearObjects("00001");
		OrderPoolManager.clearObjects("00002");
	}

    @Test
    public void testReUseOrderObject() throws InterruptedException {
		assertEquals(0, OrderPoolManager.getFreeTradeCount("00001"));
		assertEquals(0, OrderPoolManager.getUsedTradeCount("00001"));
        orderMatching.matchTopOrder();
		assertEquals(2, OrderPoolManager.getFreeOrderCount("00001"));
		assertEquals(0, OrderPoolManager.getUsedOrderCount("00001"));
		assertEquals(0, OrderPoolManager.getFreeTradeCount("00001"));
		assertEquals(1, OrderPoolManager.getUsedTradeCount("00001"));

		Order askOrder1 = RandomOrderRequestGenerator.getNewLimitOrder("00001", "Broker 2",
				"001", "S", 8.0, 100);
		assertEquals(1, OrderPoolManager.getFreeOrderCount("00001"));
		assertEquals(1, OrderPoolManager.getUsedOrderCount("00001"));

		// stock 00002
		Order askOrder2 = RandomOrderRequestGenerator.getNewLimitOrder("00002", "Broker 2",
				"001", "S", 8.0, 100);
		assertEquals(1, OrderPoolManager.getFreeOrderCount("00001"));
		assertEquals(1, OrderPoolManager.getUsedOrderCount("00001"));

		assertEquals(0, OrderPoolManager.getFreeOrderCount("00002"));
		assertEquals(1, OrderPoolManager.getUsedOrderCount("00002"));
    }

	@Test
  	public void testReUseTradeObject() throws Exception {
		doReturn(100).when(fileChannelService).writeTradeToFile(any(Trade.class), any(Path.class));
		orderMatching.matchTopOrder();
		assertEquals(0, OrderPoolManager.getFreeTradeCount("00001"));
		assertEquals(1, OrderPoolManager.getUsedTradeCount("00001"));
		Trade testTrade = tradeDataQueue.take();
		verify(fileChannelService, never()).writeTradeToFile(any(), any());
		resultingTradeJob.processTradeData(testTrade);
		verify(fileChannelService, times(1)).writeTradeToFile(eq(testTrade), any(Path.class));
		assertEquals(1, OrderPoolManager.getFreeTradeCount("00001"));
		assertEquals(0, OrderPoolManager.getUsedTradeCount("00001"));

		// 2nd trade
		OrderBook orderBook = orderBooks.get("00001");
        orderMatching = new LimitOrderMatchingJob(orderBook, orderObjMapper, marketDataQueue, tradeDataQueue);
		Order bidOrder1 = RandomOrderRequestGenerator.getNewLimitOrder("00001", "Broker 1",
				"001", "B", 8.3, 300);
        orderProcessingJob.putOrder(bidOrder1);

		Order askOrder1 = RandomOrderRequestGenerator.getNewLimitOrder("00001", "Broker 2",
				"002", "S", 8.3, 300);
        orderProcessingJob.putOrder(askOrder1);
		orderMatching.matchTopOrder();
		Trade testTrade2 = tradeDataQueue.take();
		resultingTradeJob.processTradeData(testTrade2);
		assertEquals(1, OrderPoolManager.getFreeTradeCount("00001"));
		assertEquals(0, OrderPoolManager.getUsedTradeCount("00001"));
	}
}
