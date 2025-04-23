package com.baml.equity.orderprocessing;

import equity.client.RandomOrderRequestGenerator;
import equity.orderprocessing.LimitOrderMatchingJob;
import equity.orderprocessing.OrderProcessingJob;
import equity.vo.MarketData;
import equity.vo.Order;
import equity.vo.OrderBook;
import equity.vo.Trade;
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
        orderObjMapper.put(bidOrder1.getBrokerId() + "-" + bidOrder1.getClientOrdID(), bidOrder1);
		LinkedList<Order> orderList1 = new LinkedList<>();
		orderList1.add(bidOrder1);
		orderBook.getBidMap().put(bidOrder1.getPrice(), orderList1);

		Order askOrder1 = RandomOrderRequestGenerator.getNewLimitOrder("00001", "Broker 2",
				"002", "S", 8.2, 100);
        orderObjMapper.put(askOrder1.getBrokerId() + "-" + askOrder1.getClientOrdID(), askOrder1);
		LinkedList<Order> askOrderList1 = new LinkedList<>();
		askOrderList1.add(askOrder1);
		orderBook.getAskMap().put(askOrder1.getPrice(), askOrderList1);

		Order askOrder2 = RandomOrderRequestGenerator.getNewLimitOrder("00001", "Broker 2",
				"003", "S", 8.4, 300);
        orderObjMapper.put(askOrder2.getBrokerId() + "-" + askOrder2.getClientOrdID(), askOrder2);
		LinkedList<Order> askOrderList2 = new LinkedList<>();
		askOrderList2.add(askOrder2);
		orderBook.getAskMap().put(askOrder2.getPrice(), askOrderList2);

		Order askOrder3 = RandomOrderRequestGenerator.getNewLimitOrder("00001", "Broker 2",
				"004", "S", 8.5, 400);
        orderObjMapper.put(askOrder3.getBrokerId() + "-" + askOrder3.getClientOrdID(), askOrder3);

		LinkedList<Order> orderList3 = new LinkedList<>();
		orderList3.add(askOrder3);
		orderBook.getAskMap().put(askOrder3.getPrice(), orderList3);
	}

	/**
	 * This method tests the functionality of placing a market bid order in the system.
	 * It generates a new market bid order and puts it into the order processing job.
	 * It then matches the top order and validates the market data and trade data generated.
	 *
	 * @throws InterruptedException if the thread is interrupted while waiting
	 */
	@Test
    public void testMarketBidOrder() throws InterruptedException {
		Order newMarketBidOrder = RandomOrderRequestGenerator.getNewMarketOrder("00001", "Broker 3",
				"001", "B",  100);
		orderProcessingJob.putOrder(newMarketBidOrder);
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
    }

	@Test
	public void testMarketOrder2() throws InterruptedException {
		Order newMarketBidOrder = RandomOrderRequestGenerator.getNewMarketOrder("00001", "Broker 3",
				"001", "B", 400);
		orderProcessingJob.putOrder(newMarketBidOrder);
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
		assertEquals("00001", trade.stockNo());
		assertEquals("Broker 1", trade.buyBrokerID());
		assertEquals("Broker 4", trade.sellBrokerID());
		assertEquals(8.1, trade.executedPrice());
		assertEquals(300, trade.executedQty());
    }


}
