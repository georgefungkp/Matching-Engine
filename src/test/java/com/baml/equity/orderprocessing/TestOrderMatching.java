package com.baml.equity.orderprocessing;

import equity.client.RandomOrderRequestGenerator;
import equity.orderprocessing.LimitOrderMatchingJob;
import equity.vo.MarketData;
import equity.vo.Order;
import equity.vo.OrderBook;
import equity.vo.Trade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Spy;

import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

public class TestOrderMatching {
//    // Bid order book
//    protected TreeMap<Double, LinkedList<Order>> bidMap = new TreeMap<>();
//    // Ask order book
//	protected TreeMap<Double, LinkedList<Order>> askMap = new TreeMap<>(Comparator.reverseOrder());

    private static final ConcurrentHashMap<String, Order> orderObjMapper = new ConcurrentHashMap<>();
	@Spy
	private LinkedBlockingQueue<MarketData>	marketDataQueue = new LinkedBlockingQueue<>();
	@Spy
	private LinkedBlockingQueue<Trade>	tradeDataQueue = new LinkedBlockingQueue<>();

    LimitOrderMatchingJob orderMatching;

    @BeforeEach
	protected void setUp() {
        OrderBook orderBook = new OrderBook(String.format("%05d", 1), "Stock " + 1);
        orderMatching = new LimitOrderMatchingJob(orderBook, orderObjMapper, marketDataQueue, tradeDataQueue);
		Order bidOrder1 = RandomOrderRequestGenerator.getNewLimitOrder();
		bidOrder1.setBrokerId("Broker 1");
		bidOrder1.setStockNo("001");
		bidOrder1.setPrice(8.5);
		bidOrder1.setQuantity(300);
        orderObjMapper.put(bidOrder1.getBrokerId() + bidOrder1.getClientOrdID(), bidOrder1);

		LinkedList<Order> orderList1 = new LinkedList<>();
		orderList1.add(bidOrder1);
		orderBook.getBidMap().put(bidOrder1.getPrice(), orderList1);

		Order askOrder1 = RandomOrderRequestGenerator.getNewLimitOrder();
		askOrder1.setBrokerId("Broker 2");
		askOrder1.setStockNo("00001");
		askOrder1.setPrice(8.2);
		askOrder1.setQuantity(100);
        orderObjMapper.put(askOrder1.getBrokerId() + askOrder1.getClientOrdID(), askOrder1);

		Order askOrder2 = RandomOrderRequestGenerator.getNewLimitOrder();
		askOrder2.setBrokerId("Broker 2");
		askOrder2.setStockNo("00001");
		askOrder2.setPrice(8.5);
		askOrder2.setQuantity(300);
        orderObjMapper.put(askOrder2.getBrokerId() + askOrder2.getClientOrdID(), askOrder2);

		Order askOrder3 = RandomOrderRequestGenerator.getNewLimitOrder();
		askOrder3.setBrokerId("Broker 2");
		askOrder3.setStockNo("00001");
		askOrder3.setPrice(8.5);
		askOrder3.setQuantity(400);
        orderObjMapper.put(askOrder3.getBrokerId() + askOrder3.getClientOrdID(), askOrder3);

		LinkedList<Order> orderList2 = new LinkedList<>();
		orderList2.add(askOrder1);
		orderBook.getAskMap().put(askOrder1.getPrice(), orderList2);
		LinkedList<Order> orderList3 = new LinkedList<>();
		orderList3.add(askOrder2);
		orderList3.add(askOrder3);
		orderBook.getAskMap().put(askOrder2.getPrice(), orderList3);
	}

    @Test
	public void testNoNewBid() throws InterruptedException {
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

		// Second cycle
		orderMatching.matchTopOrder();
		MarketData marketData2 = marketDataQueue.poll();
        assertNotNull(marketData2);
        assertNull(marketData2.bestBid());
        assertEquals(8.5, marketData2.bestAsk());
        assertEquals(8.5, marketData2.lastTradePrice());
		assertEquals(0, marketData2.bidMap().size());
		assertEquals(2, marketData2.askMap().get(8.5).size());
		Order order = marketData2.askMap().get(8.5).peek();
		assertNotNull(order);
		assertEquals(100, order.getQuantity());

		Trade trade2 = tradeDataQueue.poll();
		assertNotNull(trade2);
		assertEquals("00001", trade.stockNo());
		assertEquals(8.5, trade2.executedPrice());
		assertEquals(200, trade2.executedQty());

	}

}
