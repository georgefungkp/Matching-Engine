package com.baml.equity.orderprocessing;

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
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

public class TestMarketOrderMatching {
    private static final ConcurrentHashMap<String, Order> orderObjMapper = new ConcurrentHashMap<>();
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
        orderProcessingJob = new OrderProcessingJob(orderQueue, orderBooks, orderObjMapper);
		for (int i=1; i<=noOfStocks; i++ ) {
			orderBooks.put(String.format("%05d", i), new OrderBook(String.format("%05d", i), "Stock " + 1));
		}
		OrderBook orderBook = orderBooks.get("00001");
        orderMatching = new LimitOrderMatchingJob(orderBook, orderObjMapper, marketDataQueue, tradeDataQueue);
		Order bidOrder1 = RandomOrderRequestGenerator.getNewLimitOrder("00001", "Broker 1",
				"001", "B", 8.1, 300);
        orderObjMapper.put(bidOrder1.getBrokerID() + "-" + bidOrder1.getClientOrdID(), bidOrder1);
		LinkedList<Order> orderList1 = new LinkedList<>();
		orderList1.add(bidOrder1);
		orderBook.getBidMap().put(bidOrder1.getPrice(), orderList1);

		Order askOrder1 = RandomOrderRequestGenerator.getNewLimitOrder("00001", "Broker 2",
				"002", "S", 8.2, 100);
        orderObjMapper.put(askOrder1.getBrokerID() + "-" + askOrder1.getClientOrdID(), askOrder1);
		LinkedList<Order> askOrderList1 = new LinkedList<>();
		askOrderList1.add(askOrder1);
		orderBook.getAskMap().put(askOrder1.getPrice(), askOrderList1);

		Order askOrder2 = RandomOrderRequestGenerator.getNewLimitOrder("00001", "Broker 2",
				"003", "S", 8.4, 300);
        orderObjMapper.put(askOrder2.getBrokerID() + "-" + askOrder2.getClientOrdID(), askOrder2);
		LinkedList<Order> askOrderList2 = new LinkedList<>();
		askOrderList2.add(askOrder2);
		orderBook.getAskMap().put(askOrder2.getPrice(), askOrderList2);

		Order askOrder3 = RandomOrderRequestGenerator.getNewLimitOrder("00001", "Broker 2",
				"004", "S", 8.5, 400);
        orderObjMapper.put(askOrder3.getBrokerID() + "-" + askOrder3.getClientOrdID(), askOrder3);

		LinkedList<Order> orderList3 = new LinkedList<>();
		orderList3.add(askOrder3);
		orderBook.getAskMap().put(askOrder3.getPrice(), orderList3);
		assertEquals(0, OrderManager.getFreeOrderCount("00001"));
		assertEquals(4, OrderManager.getUsedOrderCount("00001"));
	}


	/**
	 * Clears data structures and objects related to order processing, market data, and trade data after each test.
	 */
	@AfterEach
	public void tearDown(){
		orderObjMapper.clear();
		marketDataQueue.clear();
        tradeDataQueue.clear();
		OrderManager.clearOrderObjects("00001");
		OrderManager.clearOrderObjects("00002");
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
		orderObjMapper.put(newMarketBIDOrder.getBrokerID() + "-" + newMarketBIDOrder.getClientOrdID(), newMarketBIDOrder);
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
		assertEquals("00001", trade.stockNo());
		assertEquals("Broker 3", trade.buyBrokerID());
		assertEquals("Broker 2", trade.sellBrokerID());
		assertEquals(8.2, trade.executedPrice());
		assertEquals(100, trade.executedQty());

		assertEquals(2, OrderManager.getFreeOrderCount("00001"));
		assertEquals(3, OrderManager.getUsedOrderCount("00001"));
    }

	@Test
	public void testMarketBidOrder2() throws InterruptedException {
		Order newMarketBIDOrder = RandomOrderRequestGenerator.getNewMarketOrder("00001", "Broker 3",
				"001", "B", 400);
		orderObjMapper.put(newMarketBIDOrder.getBrokerID() + "-" + newMarketBIDOrder.getClientOrdID(), newMarketBIDOrder);
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
		assertEquals("00001", trade.stockNo());
		assertEquals("Broker 3", trade.buyBrokerID());
		assertEquals("003", trade.sellOrderID());
		assertEquals(8.4, trade.executedPrice());
		assertEquals(300, trade.executedQty());

        orderMatching.matchTopOrder();
		Trade trade2 = tradeDataQueue.poll();
		assertNull(trade2);

		assertEquals(3, OrderManager.getFreeOrderCount("00001"));
		assertEquals(2, OrderManager.getUsedOrderCount("00001"));
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
		orderObjMapper.put(newMarketBIDOrder.getBrokerID() + "-" + newMarketBIDOrder.getClientOrdID(), newMarketBIDOrder);
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
		assertEquals("00001", trade.stockNo());
		assertEquals("Broker 3", trade.buyBrokerID());
		assertEquals("Broker 2", trade.sellBrokerID());
		assertEquals(8.2, trade.executedPrice());
		assertEquals(100, trade.executedQty());

		assertEquals(2, OrderManager.getFreeOrderCount("00001"));
		assertEquals(3, OrderManager.getUsedOrderCount("00001"));

		// Re-use order object
		Order newMarketBIDOrder2 = RandomOrderRequestGenerator.getNewMarketOrder("00001", "Broker 3",
				"001", "B",  700);
		orderObjMapper.put(newMarketBIDOrder2.getBrokerID() + "-" + newMarketBIDOrder2.getClientOrdID(), newMarketBIDOrder2);
		orderProcessingJob.putOrder(newMarketBIDOrder2);
		assertEquals(8.5, orderBooks.get("00001").getBestBid());
		assertEquals(2, OrderManager.getFreeOrderCount("00001"));
		assertEquals(3, OrderManager.getUsedOrderCount("00001"));

		// Fulfill remaining sell orders
		orderMatching.matchTopOrder();
		orderMatching.matchTopOrder();
		MarketData marketData2 = marketDataQueue.poll();
		assertNotNull(marketData2);
        assertEquals(8.1, marketData2.bestBid());
		assertNull(marketData2.bestAsk());
		assertEquals(8.5, marketData2.lastTradePrice());

		Trade trade2 = tradeDataQueue.poll();
		assertNotNull(trade2);
		assertEquals("00001", trade2.stockNo());
		assertEquals("Broker 3", trade2.buyBrokerID());
		assertEquals("Broker 2", trade2.sellBrokerID());
		assertEquals(8.4, trade2.executedPrice());
		assertEquals(300, trade2.executedQty());

		Trade trade3 = tradeDataQueue.poll();
		assertNotNull(trade3);
		assertEquals("00001", trade3.stockNo());
		assertEquals("Broker 3", trade3.buyBrokerID());
		assertEquals("Broker 2", trade3.sellBrokerID());
		assertEquals(8.5, trade3.executedPrice());
		assertEquals(400, trade3.executedQty());

		assertEquals(4, OrderManager.getFreeOrderCount("00001"));
		assertEquals(1, OrderManager.getUsedOrderCount("00001"));

    }

	@Test
    public void testMarketSellOrder() throws InterruptedException {
		Order newMarketSellOrder = RandomOrderRequestGenerator.getNewMarketOrder("00001", "Broker 4",
				"001", "S", 400);
		orderObjMapper.put(newMarketSellOrder.getBrokerID() + "-" + newMarketSellOrder.getClientOrdID(), newMarketSellOrder);
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
		assertEquals("00001", trade.stockNo());
		assertEquals("Broker 1", trade.buyBrokerID());
		assertEquals("Broker 4", trade.sellBrokerID());
		assertEquals(8.1, trade.executedPrice());
		assertEquals(300, trade.executedQty());

		assertEquals(1, OrderManager.getFreeOrderCount("00001"));
		assertEquals(4, OrderManager.getUsedOrderCount("00001"));
    }


}
