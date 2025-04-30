package com.baml.equity.orderprocessing;

import equity.client.RandomOrderRequestGenerator;
import equity.objectpooling.*;
import equity.orderprocessing.LimitOrderMatchingJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Spy;

import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestObjectPool {
    private static final ConcurrentHashMap<String, Order> orderObjMapper = new ConcurrentHashMap<>();
	@Spy
	private LinkedBlockingQueue<MarketData> marketDataQueue = new LinkedBlockingQueue<>();
	@Spy
	private LinkedBlockingQueue<Trade>	tradeDataQueue = new LinkedBlockingQueue<>();

    LimitOrderMatchingJob orderMatching;
	int noOfStocks = 2;
	OrderBook[] orderBooks = new OrderBook[noOfStocks];

    @BeforeEach
	protected void setUp() {
        for (int i = 1; i <= noOfStocks; i++) {
            orderBooks[i - 1] = new OrderBook(String.format("%05d", i), "Stock " + 1);
        }
        OrderBook orderBook = orderBooks[0];
        orderMatching = new LimitOrderMatchingJob(orderBook, orderObjMapper, marketDataQueue, tradeDataQueue);
        Order bidOrder1 = RandomOrderRequestGenerator.getNewMarketOrder("00001", "Broker 1",
                "001", "B",  300);
        orderObjMapper.put(bidOrder1.getBrokerID() + "-" + bidOrder1.getClientOrdID(), bidOrder1);
        LinkedList<Order> bidOrderList = new LinkedList<>();
        bidOrderList.add(bidOrder1);
        orderBook.getBidMap().put(bidOrder1.getPrice(), bidOrderList);

        Order askOrder1 = RandomOrderRequestGenerator.getNewMarketOrder("00001", "Broker 2",
                "001", "S",  300);
        orderObjMapper.put(askOrder1.getBrokerID() + "-" + askOrder1.getClientOrdID(), askOrder1);
        LinkedList<Order> askOrderList1 = new LinkedList<>();
        askOrderList1.add(askOrder1);
        orderBook.getBidMap().put(askOrder1.getPrice(), askOrderList1);
		assertEquals(0, OrderManager.getFreeOrderCount("00001"));
		assertEquals(2, OrderManager.getUsedOrderCount("00001"));
    }

    @Test



}
