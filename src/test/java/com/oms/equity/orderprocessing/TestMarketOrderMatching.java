package com.oms.equity.orderprocessing;

import equity.client.RandomOrderRequestGenerator;
import equity.objectpooling.*;
import equity.orderprocessing.LimitOrderMatchingJob;
import equity.orderprocessing.OrderProcessingJob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Spy;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

public class TestMarketOrderMatching {
    @Mock
    private final LinkedBlockingQueue<Order> orderQueue = new LinkedBlockingQueue<>();
	@Spy
	private final LinkedBlockingQueue<MarketData> marketDataQueue = new LinkedBlockingQueue<>();
	@Spy
	private final LinkedBlockingQueue<Trade>	tradeDataQueue = new LinkedBlockingQueue<>();

    OrderProcessingJob orderProcessingJob;
    LimitOrderMatchingJob orderMatching;
	int noOfStocks = 2;
	Map<String, OrderBook> orderBooks = new HashMap<>();

    @BeforeEach
	protected void setUp() {
		ConcurrentHashMap<String, Order> orderObjMapper = new ConcurrentHashMap<>();
		for (int i=1; i<=noOfStocks; i++ ) {
			orderBooks.put(String.format("%05d", i), new OrderBook(String.format("%05d", i), "Stock " + 1));
		}
        orderProcessingJob = new OrderProcessingJob(orderQueue, orderBooks, orderObjMapper);
		OrderBook orderBook = orderBooks.get("00001");
        orderMatching = new LimitOrderMatchingJob(orderBook, orderObjMapper, marketDataQueue, tradeDataQueue);
		Order bidLimitedOrder = RandomOrderRequestGenerator.getNewLimitOrder("00001", "Broker 1",
				"001", "B", 8.1, 300);
        orderProcessingJob.putOrder(bidLimitedOrder);

		Order askLimitedOrder1 = RandomOrderRequestGenerator.getNewLimitOrder("00001", "Broker 2",
				"002", "S", 8.2, 100);
        orderProcessingJob.putOrder(askLimitedOrder1);

		Order askLimitedOrder2 = RandomOrderRequestGenerator.getNewLimitOrder("00001", "Broker 2",
				"003", "S", 8.4, 300);
        orderProcessingJob.putOrder(askLimitedOrder2);

		Order askLimitedOrder3 = RandomOrderRequestGenerator.getNewLimitOrder("00001", "Broker 2",
				"004", "S", 8.5, 400);
        orderObjMapper.put(askLimitedOrder3.getBrokerID() + "-" + askLimitedOrder3.getClientOrdID(), askLimitedOrder3);
        orderProcessingJob.putOrder(askLimitedOrder3);

		assertEquals(0, OrderPoolManager.getFreeOrderCount("00001"));
		assertEquals(4, OrderPoolManager.getUsedOrderCount("00001"));
		assertEquals(0, OrderPoolManager.getFreeTradeCount("00002"));
		assertEquals(0, OrderPoolManager.getUsedTradeCount("00002"));
	}


	/**
	 * Clears data structures and objects related to order processing, market data, and trade data after each test.
	 */
	@AfterEach
	public void tearDown(){
		marketDataQueue.clear();
        tradeDataQueue.clear();
		OrderPoolManager.clearObjects("00001");
		OrderPoolManager.clearObjects("00002");
	}


	/**
	 * This method tests the functionality of placing a market bID order in the system.
	 * It generates a new market bID order and puts it into the order processing job.
	 * It then matches the top order and valIDates the market data and trade data generated.
	 *
	 * @throws InterruptedException if the thread is interrupted while waiting
	 */
	@Test
    public void testMarketBidOrder() throws InterruptedException {
		Order newMarketBIDOrder = RandomOrderRequestGenerator.getNewMarketOrder("00001", "Broker 3",
				"001", "B",  100);
		orderProcessingJob.putOrder(newMarketBIDOrder);
		assertEquals(8.5, orderBooks.get("00001").getBestBid());
        orderMatching.matchTopOrder();
		MarketData marketData = marketDataQueue.poll();
		assertNotNull(marketData);
        assertEquals(8.1, marketData.bestBid());
		assertEquals(8.4, marketData.bestAsk());
		assertEquals(8.2, marketData.lastTradePrice());

		Trade trade = tradeDataQueue.poll();
		assertNotNull(trade);
		assertEquals("00001", trade.getStockNo());
		assertEquals("Broker 3", trade.getBuyBrokerID());
		assertEquals("Broker 2", trade.getSellBrokerID());
		assertEquals(8.2, trade.getExecutedPrice());
		assertEquals(100, trade.getExecutedQty());

		assertEquals(2, OrderPoolManager.getFreeOrderCount("00001"));
		assertEquals(3, OrderPoolManager.getUsedOrderCount("00001"));
		assertEquals(0, OrderPoolManager.getFreeTradeCount("00001"));
		assertEquals(1, OrderPoolManager.getUsedTradeCount("00001"));
    }

	@Test
	public void testMarketBidOrder2() throws InterruptedException {
		Order newMarketBIDOrder = RandomOrderRequestGenerator.getNewMarketOrder("00001", "Broker 3",
				"001", "B", 400);
		orderProcessingJob.putOrder(newMarketBIDOrder);
        orderMatching.matchTopOrder();
		marketDataQueue.poll();
		tradeDataQueue.poll();
        orderMatching.matchTopOrder();
		MarketData marketData = marketDataQueue.poll();
		assertNotNull(marketData);
        assertEquals(8.1, marketData.bestBid());
		assertEquals(8.5, marketData.bestAsk());
		assertEquals(8.4, marketData.lastTradePrice());

		Trade trade = tradeDataQueue.poll();
		assertNotNull(trade);
		assertEquals("00001", trade.getStockNo());
		assertEquals("Broker 3", trade.getBuyBrokerID());
		assertEquals("003", trade.getSellOrderID());
		assertEquals(8.4, trade.getExecutedPrice());
		assertEquals(300, trade.getExecutedQty());

        orderMatching.matchTopOrder();
		Trade trade2 = tradeDataQueue.poll();
		assertNull(trade2);

		assertEquals(3, OrderPoolManager.getFreeOrderCount("00001"));
		assertEquals(2, OrderPoolManager.getUsedOrderCount("00001"));
    }

	/**
	 * Method to test the functionality of placing a market bid order in the system.
	 * It generates a new market bid order and puts it into the order processing job.
	 * It then matches the top order and validates the market data and trade data generated.
	 * After order is matched, the order object will return to pool and re-used again in the next request.
	 * The total number of available order objects are the same.
	 *
	 * @throws InterruptedException if the thread is interrupted while waiting
	 */
	@Test
    public void testMarketBidOrder3() throws InterruptedException {
		Order newMarketBIDOrder = RandomOrderRequestGenerator.getNewMarketOrder("00001", "Broker 3",
				"001", "B",  100);
		orderProcessingJob.putOrder(newMarketBIDOrder);
		assertEquals(8.5, orderBooks.get("00001").getBestBid());

        orderMatching.matchTopOrder();
		MarketData marketData = marketDataQueue.poll();
		assertNotNull(marketData);
        assertEquals(8.1, marketData.bestBid());
		assertEquals(8.4, marketData.bestAsk());
		assertEquals(8.2, marketData.lastTradePrice());

		Trade trade = tradeDataQueue.poll();
		assertNotNull(trade);
		assertEquals("00001", trade.getStockNo());
		assertEquals("Broker 3", trade.getBuyBrokerID());
		assertEquals("Broker 2", trade.getSellBrokerID());
		assertEquals(8.2, trade.getExecutedPrice());
		assertEquals(100, trade.getExecutedQty());

		assertEquals(2, OrderPoolManager.getFreeOrderCount("00001"));
		assertEquals(3, OrderPoolManager.getUsedOrderCount("00001"));

		// Re-use order object
		Order newMarketBIDOrder2 = RandomOrderRequestGenerator.getNewMarketOrder("00001", "Broker 3",
				"001", "B",  700);
		orderProcessingJob.putOrder(newMarketBIDOrder2);
		assertEquals(8.5, orderBooks.get("00001").getBestBid());
		assertEquals(1, OrderPoolManager.getFreeOrderCount("00001"));
		assertEquals(4, OrderPoolManager.getUsedOrderCount("00001"));

		// Fulfill remaining sell orders
		orderMatching.matchTopOrder();
		orderMatching.matchTopOrder();
		MarketData marketData2 = marketDataQueue.poll();
		assertNotNull(marketData2);
        assertEquals(8.5, marketData2.bestBid());
        assertEquals(8.5, marketData2.bestAsk());
		assertEquals(8.4, marketData2.lastTradePrice());

		Trade trade2 = tradeDataQueue.poll();
		assertNotNull(trade2);
		assertEquals("00001", trade2.getStockNo());
		assertEquals("Broker 3", trade2.getBuyBrokerID());
		assertEquals("Broker 2", trade2.getSellBrokerID());
		assertEquals(8.4, trade2.getExecutedPrice());
		assertEquals(300, trade2.getExecutedQty());

		MarketData marketData3 = marketDataQueue.poll();
		assertNotNull(marketData3);
        assertEquals(8.1, marketData3.bestBid());
		assertNull(marketData3.bestAsk());
		assertEquals(8.5, marketData3.lastTradePrice());

		Trade trade3 = tradeDataQueue.poll();
		assertNotNull(trade3);
		assertEquals("00001", trade3.getStockNo());
		assertEquals("Broker 3", trade3.getBuyBrokerID());
		assertEquals("Broker 2", trade3.getSellBrokerID());
		assertEquals(8.5, trade3.getExecutedPrice());
		assertEquals(400, trade3.getExecutedQty());

		assertEquals(4, OrderPoolManager.getFreeOrderCount("00001"));
		assertEquals(1, OrderPoolManager.getUsedOrderCount("00001"));
		assertEquals(0, OrderPoolManager.getFreeTradeCount("00001"));
		assertEquals(3, OrderPoolManager.getUsedTradeCount("00001"));


    }

	@Test
    public void testMarketSellOrder() throws InterruptedException {
		Order newMarketSellOrder = RandomOrderRequestGenerator.getNewMarketOrder("00001", "Broker 4",
				"001", "S", 400);
		orderProcessingJob.putOrder(newMarketSellOrder);
		assertEquals(8.1, orderBooks.get("00001").getBestAsk());
        orderMatching.matchTopOrder();
		MarketData marketData = marketDataQueue.poll();
		assertNotNull(marketData);
		assertNull(marketData.bestBid());
		assertEquals(8.1, marketData.bestAsk());
		assertEquals(8.1, marketData.lastTradePrice());

		Trade trade = tradeDataQueue.poll();
		assertNotNull(trade);
		assertEquals("00001", trade.getStockNo());
		assertEquals("Broker 1", trade.getBuyBrokerID());
		assertEquals("Broker 4", trade.getSellBrokerID());
		assertEquals(8.1, trade.getExecutedPrice());
		assertEquals(300, trade.getExecutedQty());

		assertEquals(1, OrderPoolManager.getFreeOrderCount("00001"));
		assertEquals(4, OrderPoolManager.getUsedOrderCount("00001"));
    }


}
