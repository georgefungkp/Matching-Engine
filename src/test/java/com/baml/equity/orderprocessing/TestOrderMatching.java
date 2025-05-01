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

public class TestOrderMatching {
    private static final ConcurrentHashMap<String, Order> orderObjMapper = new ConcurrentHashMap<>();
    @Mock
    private final LinkedBlockingQueue<Order> orderQueue = new LinkedBlockingQueue<>();
	@Spy
	private LinkedBlockingQueue<MarketData>	marketDataQueue = new LinkedBlockingQueue<>();
	@Spy
	private LinkedBlockingQueue<Trade>	tradeDataQueue = new LinkedBlockingQueue<>();

	OrderProcessingJob orderProcessingJob;
    LimitOrderMatchingJob orderMatching;
	int noOfStocks = 2;
	Map<String, OrderBook> orderBooks = new HashMap<>();

	/**
	 * Set up method to prepare order books, order matching job, and sample orders for testing.
	 * Initializes order books for each stock with predefined bID and ask orders.
	 * Creates a LimitOrderMatchingJob for order matching simulation.
	 */
    @BeforeEach
	protected void setUp() {
		for (int i=1; i<=noOfStocks; i++ ) {
			orderBooks.put(String.format("%05d", i), new OrderBook(String.format("%05d", i), "Stock " + 1));
		}
        orderProcessingJob = new OrderProcessingJob(orderQueue, orderBooks, orderObjMapper);
		OrderBook orderBook = orderBooks.get("00001");
        orderMatching = new LimitOrderMatchingJob(orderBook, orderObjMapper, marketDataQueue, tradeDataQueue);

		Order bidLimitedOrder = RandomOrderRequestGenerator.getNewLimitOrder("00001", "Broker 1",
				"001", "B", 8.5, 300);
        orderProcessingJob.putOrder(bidLimitedOrder);
		Order bidLimitedOrder2 = RandomOrderRequestGenerator.getNewLimitOrder("00001", "Broker 1",
				"002", "B", 8.1, 100);
        orderProcessingJob.putOrder(bidLimitedOrder2);

		Order askLimitedOrder1 = RandomOrderRequestGenerator.getNewLimitOrder("00001", "Broker 2",
				"002", "S", 8.2, 100);
        orderProcessingJob.putOrder(askLimitedOrder1);
		Order askLimitedOrder2 = RandomOrderRequestGenerator.getNewLimitOrder("00001", "Broker 2",
				"003", "S", 8.5, 300);
		Order askLimitedOrder3 = RandomOrderRequestGenerator.getNewLimitOrder("00001", "Broker 2",
				"004", "S", 8.5, 400);
        orderProcessingJob.putOrder(askLimitedOrder2);
        orderProcessingJob.putOrder(askLimitedOrder3);
		assertEquals(0, OrderManager.getFreeOrderCount("00001"));
		assertEquals(5, OrderManager.getUsedOrderCount("00001"));
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


	/**
	 * Method for testing the partial fill functionality of order matching.
	 * This method simulates multiple cycles of matching top bID and ask orders,
	 * verifying market data updates and trade executions each time.
	 *
	 * @throws InterruptedException if a thread is interrupted while waiting
	 */
    @Test
	public void testPartialFill() throws InterruptedException {
		// First cycle
		orderMatching.matchTopOrder();
		MarketData marketData = marketDataQueue.poll();
		assertNotNull(marketData);
        assertEquals(8.5, marketData.bestBid());
		assertEquals(8.5, marketData.bestAsk());
		assertEquals(8.2, marketData.lastTradePrice());

		Trade trade = tradeDataQueue.poll();
		assertNotNull(trade);
		assertEquals("00001", trade.getStockNo());
		assertEquals(8.2, trade.getExecutedPrice());
		assertEquals(100, trade.getExecutedQty());
		assertEquals(1, OrderManager.getFreeOrderCount("00001"));
		assertEquals(4, OrderManager.getUsedOrderCount("00001"));

		// Second cycle
		orderMatching.matchTopOrder();
		MarketData marketData2 = marketDataQueue.poll();
        assertNotNull(marketData2);
        assertEquals(8.1, marketData2.bestBid());
        assertEquals(8.5, marketData2.bestAsk());
        assertEquals(8.5, marketData2.lastTradePrice());
		assertEquals(1, marketData2.bidMap().size());
		assertEquals(2, marketData2.askMap().get(8.5).size());
		Order order = marketData2.askMap().get(8.5).peek();
		assertNotNull(order);
		assertEquals(100, order.getQuantity());

		Trade trade2 = tradeDataQueue.poll();
		assertNotNull(trade2);
		assertEquals("00001", trade.getStockNo());
		assertEquals(8.5, trade2.getExecutedPrice());
		assertEquals(200, trade2.getExecutedQty());
		assertEquals(2, OrderManager.getFreeOrderCount("00001"));
		assertEquals(3, OrderManager.getUsedOrderCount("00001"));

		// Third and subsequent
		orderMatching.matchTopOrder();
        assertNull(marketDataQueue.poll());
		assertNull(tradeDataQueue.poll());
		assertEquals(2, OrderManager.getFreeOrderCount("00001"));
	}

	@Test
	public void testEmptyBook() throws InterruptedException {
		OrderBook orderBook = new OrderBook(String.format("%05d", 0), "Stock " + 1);
        orderMatching = new LimitOrderMatchingJob(orderBook, orderObjMapper, marketDataQueue, tradeDataQueue);
		orderMatching.matchTopOrder();
        assertNull(marketDataQueue.poll());
		assertNull(tradeDataQueue.poll());
	}

	@Test
	public void testEmptyBIDQueue() throws InterruptedException {
		OrderBook orderBook = new OrderBook(String.format("%05d", 0), "Stock " + 1);
        orderMatching = new LimitOrderMatchingJob(orderBook, orderObjMapper, marketDataQueue, tradeDataQueue);
		Order bidOrder1 = RandomOrderRequestGenerator.getNewLimitOrder("00000", "Broker 1",
				"001", "B", 8.2, 100);
		orderObjMapper.put(bidOrder1.getBrokerID() + "-" + bidOrder1.getClientOrdID(), bidOrder1);

		LinkedList<Order> orderList1 = new LinkedList<>();
		orderList1.add(bidOrder1);
		orderBook.getBidMap().put(bidOrder1.getPrice(), orderList1);
		orderMatching.matchTopOrder();
        assertNull(marketDataQueue.poll());
		assertNull(tradeDataQueue.poll());
	}

	@Test
	public void testEmptyAskQueue() throws InterruptedException {
		OrderBook orderBook = new OrderBook(String.format("%05d", 0), "Stock " + 1);
        orderMatching = new LimitOrderMatchingJob(orderBook, orderObjMapper, marketDataQueue, tradeDataQueue);
		Order askOrder1 = RandomOrderRequestGenerator.getNewLimitOrder("00000", "Broker 1",
				"001", "S", 8.2, 100);
		orderObjMapper.put(askOrder1.getBrokerID() + "-" + askOrder1.getClientOrdID(), askOrder1);

		LinkedList<Order> orderList1 = new LinkedList<>();
		orderList1.add(askOrder1);
		orderBook.getBidMap().put(askOrder1.getPrice(), orderList1);
		orderMatching.matchTopOrder();
        assertNull(marketDataQueue.poll());
		assertNull(tradeDataQueue.poll());
	}

	/**
	 * Method to test how matching engine preform on big bID order
	 *
	 * @throws InterruptedException if a thread is interrupted while waiting
	 */
	@Test
	public void testBigBIDOrder() throws InterruptedException {
		Order bidOrder2 = RandomOrderRequestGenerator.getNewLimitOrder("00001", "Broker 1",
                "005", "B", 8.6, 1000);

		orderProcessingJob.putOrder(bidOrder2);

		orderMatching.matchTopOrder();
		MarketData marketData = marketDataQueue.poll();
		assertNotNull(marketData);
        assertEquals(8.6, marketData.bestBid());
		assertEquals(8.5, marketData.bestAsk());
		assertEquals(8.2, marketData.lastTradePrice());
		Trade trade = tradeDataQueue.poll();
		assertNotNull(trade);
		assertEquals("00001", trade.getStockNo());
		assertEquals(8.2, trade.getExecutedPrice());
		assertEquals("Broker 1", trade.getBuyBrokerID());
		assertEquals("002", trade.getSellOrderID());
		assertEquals(100, trade.getExecutedQty());

		orderMatching.matchTopOrder();
		MarketData marketData2 = marketDataQueue.poll();
		assertNotNull(marketData2);
        assertEquals(8.6, marketData2.bestBid());
		assertEquals(8.5, marketData2.bestAsk());
		assertEquals(8.5, marketData2.lastTradePrice());
		Trade trade2 = tradeDataQueue.poll();
		assertNotNull(trade2);
		assertEquals("00001", trade2.getStockNo());
		assertEquals(8.5, trade2.getExecutedPrice());
		assertEquals("005", trade.getBuyOrderID());
		assertEquals("003", trade2.getSellOrderID());
		assertEquals(300, trade2.getExecutedQty());

		orderMatching.matchTopOrder();
		MarketData marketData3 = marketDataQueue.poll();
		assertNotNull(marketData3);
        assertEquals(8.6, marketData3.bestBid());
		// Settle all sell orders
		assertNull(marketData3.bestAsk());
		assertEquals(8.5, marketData3.lastTradePrice());
		Trade trade3 = tradeDataQueue.poll();
		assertNotNull(trade3);
		assertEquals("00001", trade3.getStockNo());
		assertEquals(8.5, trade3.getExecutedPrice());
		assertEquals("005", trade.getBuyOrderID());
		assertEquals("004", trade3.getSellOrderID());
		assertEquals(400, trade3.getExecutedQty());

		assertEquals(3, OrderManager.getFreeOrderCount("00001"));
		assertEquals(3, OrderManager.getUsedOrderCount("00001"));

	}

	/**
	 * Method to test the matching functionality of the order matching job by setting up a sample order book
	 * with a bID and ask order at the same price level. It then matches these orders, valIDates the market data
	 * updates, and verifies the trade details after the matching process.
	 *
	 * @throws InterruptedException if a thread is interrupted while waiting
	 */
	@Test
	public void testJustMatch() throws InterruptedException {
//		stock No 2
		OrderBook orderBook = orderBooks.get("00002");
        orderMatching = new LimitOrderMatchingJob(orderBook, orderObjMapper, marketDataQueue, tradeDataQueue);
		Order bidOrder1 = RandomOrderRequestGenerator.getNewLimitOrder("00002", "Broker 1",
				"001", "B", 8.1, 100);
		orderProcessingJob.putOrder(bidOrder1);

		Order askOrder1 = RandomOrderRequestGenerator.getNewLimitOrder("00002", "Broker 2",
				"002", "S", 8.1, 100);
		orderProcessingJob.putOrder(askOrder1);

		orderMatching.matchTopOrder();
		MarketData marketData = marketDataQueue.poll();
		assertNotNull(marketData);
        assertNull(marketData.bestBid());
		assertNull(marketData.bestAsk());
		assertEquals(8.1, marketData.lastTradePrice());
		Trade trade = tradeDataQueue.poll();
		assertNotNull(trade);
		assertEquals("00002", trade.getStockNo());
		assertEquals(8.1, trade.getExecutedPrice());
		assertEquals(100, trade.getExecutedQty());
		assertTrue(orderBook.getAskMap().isEmpty());
		assertTrue(orderBook.getBidMap().isEmpty());

		assertEquals(2, OrderManager.getFreeOrderCount("00002"));
		assertEquals(0, OrderManager.getUsedOrderCount("00002"));
		assertEquals(5, OrderManager.getUsedOrderCount("00001"));
	}
}
