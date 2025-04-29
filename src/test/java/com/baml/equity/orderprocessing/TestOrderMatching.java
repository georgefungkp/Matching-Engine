package com.baml.equity.orderprocessing;

import equity.client.RandomOrderRequestGenerator;
import equity.objectpooling.*;
import equity.orderprocessing.LimitOrderMatchingJob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Spy;

import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

public class TestOrderMatching {
    private static final ConcurrentHashMap<String, Order> orderObjMapper = new ConcurrentHashMap<>();
	@Spy
	private LinkedBlockingQueue<MarketData>	marketDataQueue = new LinkedBlockingQueue<>();
	@Spy
	private LinkedBlockingQueue<Trade>	tradeDataQueue = new LinkedBlockingQueue<>();

    LimitOrderMatchingJob orderMatching;
	int noOfStocks = 2;
	OrderBook[] orderBooks = new OrderBook[noOfStocks];

	/**
	 * Set up method to prepare order books, order matching job, and sample orders for testing.
	 * Initializes order books for each stock with predefined bID and ask orders.
	 * Creates a LimitOrderMatchingJob for order matching simulation.
	 */
    @BeforeEach
	protected void setUp() {
		for (int i=1; i<=noOfStocks; i++ ) {
			orderBooks[i-1] = new OrderBook(String.format("%05d", i), "Stock " + 1);
		}
		OrderBook orderBook = orderBooks[0];
        orderMatching = new LimitOrderMatchingJob(orderBook, orderObjMapper, marketDataQueue, tradeDataQueue);
		Order bidOrder1 = RandomOrderRequestGenerator.getNewLimitOrder("00001", "Broker 1",
				"001", "B", 8.5, 300);
        orderObjMapper.put(bidOrder1.getBrokerID() + "-" + bidOrder1.getClientOrdID(), bidOrder1);
		LinkedList<Order> orderList1 = new LinkedList<>();
		orderList1.add(bidOrder1);
		orderBook.getBidMap().put(bidOrder1.getPrice(), orderList1);

		Order bidOrder2 = RandomOrderRequestGenerator.getNewLimitOrder("00001", "Broker 1",
				"002", "B", 8.1, 100);
        orderObjMapper.put(bidOrder2.getBrokerID() + "-" + bidOrder2.getClientOrdID(), bidOrder2);
		LinkedList<Order> orderList2 = new LinkedList<>();
		orderList2.add(bidOrder2);
		orderBook.getBidMap().put(bidOrder2.getPrice(), orderList2);


		Order askOrder1 = RandomOrderRequestGenerator.getNewLimitOrder("00001", "Broker 2",
				"002", "S", 8.2, 100);
        orderObjMapper.put(askOrder1.getBrokerID() + "-" + askOrder1.getClientOrdID(), askOrder1);
		LinkedList<Order> orderList3 = new LinkedList<>();
		orderList3.add(askOrder1);
		orderBook.getAskMap().put(askOrder1.getPrice(), orderList3);


		Order askOrder2 = RandomOrderRequestGenerator.getNewLimitOrder("00001", "Broker 2",
				"003", "S", 8.5, 300);
        orderObjMapper.put(askOrder2.getBrokerID() + "-" + askOrder2.getClientOrdID(), askOrder2);
		Order askOrder3 = RandomOrderRequestGenerator.getNewLimitOrder("00001", "Broker 2",
				"004", "S", 8.5, 400);
        orderObjMapper.put(askOrder3.getBrokerID() + "-" + askOrder3.getClientOrdID(), askOrder3);
		LinkedList<Order> orderList4 = new LinkedList<>();
		orderList4.add(askOrder2);
		orderList4.add(askOrder3);
		orderBook.getAskMap().put(askOrder2.getPrice(), orderList4);
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
		OrderManager.clearOrderObjects("00001");
		OrderManager.clearOrderObjects("00002");
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
		assertEquals("00001", trade.stockNo());
		assertEquals(8.2, trade.executedPrice());
		assertEquals(100, trade.executedQty());
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
		assertEquals("00001", trade.stockNo());
		assertEquals(8.5, trade2.executedPrice());
		assertEquals(200, trade2.executedQty());
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
		orderObjMapper.put(bidOrder2.getBrokerID() + "-" + bidOrder2.getClientOrdID(), bidOrder2);

        LinkedList<Order> orderList4 = new LinkedList<>();
        orderList4.add(bidOrder2);
        orderBooks[0].getBidMap().put(bidOrder2.getPrice(), orderList4);
		orderMatching.matchTopOrder();

		MarketData marketData = marketDataQueue.poll();
		assertNotNull(marketData);
        assertEquals(8.6, marketData.bestBid());
		assertEquals(8.5, marketData.bestAsk());
		assertEquals(8.2, marketData.lastTradePrice());
		Trade trade = tradeDataQueue.poll();
		assertNotNull(trade);
		assertEquals("00001", trade.stockNo());
		assertEquals(8.2, trade.executedPrice());
		assertEquals("Broker 1", trade.buyBrokerID());
		assertEquals("002", trade.sellOrderID());
		assertEquals(100, trade.executedQty());

		orderMatching.matchTopOrder();
		MarketData marketData2 = marketDataQueue.poll();
		assertNotNull(marketData2);
        assertEquals(8.6, marketData2.bestBid());
		assertEquals(8.5, marketData2.bestAsk());
		assertEquals(8.5, marketData2.lastTradePrice());
		Trade trade2 = tradeDataQueue.poll();
		assertNotNull(trade2);
		assertEquals("00001", trade2.stockNo());
		assertEquals(8.5, trade2.executedPrice());
		assertEquals("005", trade.buyOrderID());
		assertEquals("003", trade2.sellOrderID());
		assertEquals(300, trade2.executedQty());

		orderMatching.matchTopOrder();
		MarketData marketData3 = marketDataQueue.poll();
		assertNotNull(marketData3);
        assertEquals(8.6, marketData3.bestBid());
		// Settle all sell orders
		assertNull(marketData3.bestAsk());
		assertEquals(8.5, marketData3.lastTradePrice());
		Trade trade3 = tradeDataQueue.poll();
		assertNotNull(trade3);
		assertEquals("00001", trade3.stockNo());
		assertEquals(8.5, trade3.executedPrice());
		assertEquals("005", trade.buyOrderID());
		assertEquals("004", trade3.sellOrderID());
		assertEquals(400, trade3.executedQty());

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
		OrderBook orderBook = orderBooks[1];
        orderMatching = new LimitOrderMatchingJob(orderBook, orderObjMapper, marketDataQueue, tradeDataQueue);
		Order bidOrder1 = RandomOrderRequestGenerator.getNewLimitOrder("00002", "Broker 1",
				"001", "B", 8.1, 100);
		orderObjMapper.put(bidOrder1.getBrokerID() + "-" + bidOrder1.getClientOrdID(), bidOrder1);
		LinkedList<Order> orderList1 = new LinkedList<>();
		orderList1.add(bidOrder1);
		orderBook.getBidMap().put(bidOrder1.getPrice(), orderList1);

		Order askOrder1 = RandomOrderRequestGenerator.getNewLimitOrder("00002", "Broker 2",
				"002", "S", 8.1, 100);
		orderObjMapper.put(askOrder1.getBrokerID() + "-" + askOrder1.getClientOrdID(), askOrder1);
		LinkedList<Order> orderList2 = new LinkedList<>();
		orderList2.add(askOrder1);
		orderBook.getAskMap().put(askOrder1.getPrice(), orderList2);

		orderMatching.matchTopOrder();
		MarketData marketData = marketDataQueue.poll();
		assertNotNull(marketData);
        assertNull(marketData.bestBid());
		assertNull(marketData.bestAsk());
		assertEquals(8.1, marketData.lastTradePrice());
		Trade trade = tradeDataQueue.poll();
		assertNotNull(trade);
		assertEquals("00002", trade.stockNo());
		assertEquals(8.1, trade.executedPrice());
		assertEquals(100, trade.executedQty());
		assertTrue(orderBook.getAskMap().isEmpty());
		assertTrue(orderBook.getBidMap().isEmpty());

		assertEquals(2, OrderManager.getFreeOrderCount("00002"));
		assertEquals(0, OrderManager.getUsedOrderCount("00002"));
		assertEquals(5, OrderManager.getUsedOrderCount("00001"));
	}
}
