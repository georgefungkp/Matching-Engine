package equity.orderprocessing;

import equity.client.RandomOrderRequestGenerator;
import equity.objectpooling.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Market Order Matching Tests")
public class TestMarketOrderMatching {
    
    // Constants
    private static final String STOCK_1 = "00001";
    private static final String BROKER_1 = "Broker 1";
    private static final String BROKER_2 = "Broker 2";
    private static final String BROKER_3 = "Broker 3";
    private static final String BROKER_4 = "Broker 4";
    private static final String CLIENT_ORDER_1 = "001";
    private static final String CLIENT_ORDER_2 = "002";
    private static final double PRICE_8_1 = 8.1;
    private static final double PRICE_8_2 = 8.2;
    private static final int QUANTITY_300 = 300;
    private static final int QUANTITY_400 = 400;
    private static final int NO_OF_STOCKS = 2;
    
    // Test data structures
    private static final ConcurrentHashMap<String, Order> orderObjMapper = new ConcurrentHashMap<>();
    private final LinkedBlockingQueue<Order> orderQueue = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<MarketData> marketDataQueue = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<Trade> tradeDataQueue = new LinkedBlockingQueue<>();
    
    // Test subjects
    private OrderProcessingJob orderProcessingJob;
    private LimitOrderMatchingJob orderMatching;
    private Map<String, OrderBook> orderBooks;

    @BeforeEach
    void setUp() {
        initializeOrderBooks();
        initializeTestSubjects();
        setupInitialOrders();
        verifyInitialSetup();
    }

    @AfterEach
    void tearDown() {
        clearTestData();
        clearObjectPools();
    }

    private void initializeOrderBooks() {
        orderBooks = new HashMap<>();
        for (int i = 1; i <= NO_OF_STOCKS; i++) {
            String stockId = String.format("%05d", i);
            orderBooks.put(stockId, new OrderBook(stockId, "Stock " + i));
        }
    }

    private void initializeTestSubjects() {
        orderProcessingJob = new OrderProcessingJob(orderQueue, orderBooks, orderObjMapper);
        OrderBook orderBook = orderBooks.get(STOCK_1);
        orderMatching = new LimitOrderMatchingJob(orderBook, orderObjMapper, marketDataQueue, tradeDataQueue);
    }

    private void setupInitialOrders() {
        // Create bid orders at different price levels
        Order bidOrder1 = createLimitOrder(BROKER_1, CLIENT_ORDER_1, "B", PRICE_8_1, QUANTITY_300);
        Order bidOrder2 = createLimitOrder(BROKER_3, CLIENT_ORDER_2, "B", PRICE_8_2, QUANTITY_300);
        
        orderProcessingJob.putOrder(bidOrder1);
        orderProcessingJob.putOrder(bidOrder2);
    }

    private Order createLimitOrder(String broker, String clientOrderId, String side, double price, int quantity) {
        return RandomOrderRequestGenerator.getNewLimitOrder(STOCK_1, broker, clientOrderId, side, price, quantity);
    }

    private Order createMarketOrder(String broker, String clientOrderId, String side, int quantity) {
        return RandomOrderRequestGenerator.getNewMarketOrder(STOCK_1, broker, clientOrderId, side, quantity);
    }

    private void verifyInitialSetup() {
        assertEquals(0, OrderPoolManager.getFreeOrderCount(STOCK_1));
        assertEquals(2, OrderPoolManager.getUsedOrderCount(STOCK_1));
    }

    private void clearTestData() {
        orderObjMapper.clear();
        marketDataQueue.clear();
        tradeDataQueue.clear();
    }

    private void clearObjectPools() {
        OrderPoolManager.clearObjects(STOCK_1);
        OrderPoolManager.clearObjects("00002");
    }

    @Test
    @DisplayName("Should execute market sell order against best bid")
    void testMarketSellOrder() throws InterruptedException {
        // Given - market sell order
        Order marketSellOrder = createMarketOrder(BROKER_4, CLIENT_ORDER_1, "S", QUANTITY_400);
        orderProcessingJob.putOrder(marketSellOrder);
        
        // Verify order book state before matching
        assertEquals(PRICE_8_2, orderBooks.get(STOCK_1).getBestBid());
        assertEquals(PRICE_8_2, orderBooks.get(STOCK_1).getBestAsk()); // Market order takes best bid price
        
        // When - match orders
        orderMatching.matchTopOrder();
        
        // Then - verify market data update
        MarketData marketData = marketDataQueue.poll();
        assertNotNull(marketData, "Market data should be generated after matching");
        assertNull(marketData.bestBid(), "Best bid should be null after partial fill");
        assertEquals(PRICE_8_2, marketData.bestAsk(), "Best ask should reflect remaining market order");
        assertEquals(PRICE_8_2, marketData.lastTradePrice(), "Last trade price should match execution price");

        // Verify trade execution
        Trade trade = verifyTradeExecution(BROKER_3, BROKER_4, PRICE_8_2, QUANTITY_300);
        
        // Verify object pool state
        assertEquals(1, OrderPoolManager.getFreeOrderCount(STOCK_1), "One order should be freed after full execution");
        assertEquals(4, OrderPoolManager.getUsedOrderCount(STOCK_1), "Remaining orders plus market data and trade objects");
    }

    @Test
    @DisplayName("Should execute market buy order against best ask")
    void testMarketBuyOrder() throws InterruptedException {
        // Given - setup ask orders and market buy order
        Order askOrder = createLimitOrder(BROKER_2, CLIENT_ORDER_1, "S", PRICE_8_1, QUANTITY_300);
        orderProcessingJob.putOrder(askOrder);
        
        Order marketBuyOrder = createMarketOrder(BROKER_4, CLIENT_ORDER_2, "B", QUANTITY_300);
        orderProcessingJob.putOrder(marketBuyOrder);
        
        // When - match orders
        orderMatching.matchTopOrder();
        
        // Then - verify market data
        MarketData marketData = marketDataQueue.poll();
        assertNotNull(marketData, "Market data should be generated");
        assertEquals(PRICE_8_2, marketData.bestBid(), "Best bid should remain from limit order");
        assertNull(marketData.bestAsk(), "Best ask should be null after complete fill");
        assertEquals(PRICE_8_1, marketData.lastTradePrice(), "Trade should execute at ask price");
        
        // Verify trade execution
        verifyTradeExecution(BROKER_4, BROKER_2, PRICE_8_1, QUANTITY_300);
    }

    @Test
    @DisplayName("Should handle market order when no opposite side exists")
    void testMarketOrderWithEmptyOppositeSide() {
        // Given - order book with only bid orders (no asks)
        Order marketSellOrder = createMarketOrder(BROKER_4, CLIENT_ORDER_1, "S", QUANTITY_300);
        
        // When - try to add market sell order with no asks
        orderProcessingJob.putOrder(marketSellOrder);
        
        // Then - market order should not be added due to no opposite price
        assertEquals(2, OrderPoolManager.getUsedOrderCount(STOCK_1), "No new orders should be added");
        assertTrue(marketDataQueue.isEmpty(), "No market data should be generated");
        assertTrue(tradeDataQueue.isEmpty(), "No trades should be executed");
    }

    @Test
    @DisplayName("Should handle partial fill of market order")
    void testPartialFillMarketOrder() throws InterruptedException {
        // Given - market order larger than available liquidity
        Order marketSellOrder = createMarketOrder(BROKER_4, CLIENT_ORDER_1, "S", QUANTITY_400);
        orderProcessingJob.putOrder(marketSellOrder);
        
        // When - match against smaller bid
        orderMatching.matchTopOrder();
        
        // Then - verify partial execution
        Trade trade = tradeDataQueue.poll();
        assertNotNull(trade, "Trade should be executed");
        assertEquals(QUANTITY_300, trade.getExecutedQty(), "Should execute available quantity");
        assertEquals(PRICE_8_2, trade.getExecutedPrice(), "Should execute at best bid price");
        
        // Verify remaining market order
        MarketData marketData = marketDataQueue.poll();
        assertNotNull(marketData, "Market data should reflect remaining order");
        assertEquals(PRICE_8_1, marketData.bestBid(), "Next best bid should be top of book");
        assertEquals(PRICE_8_1, marketData.bestAsk(), "Remaining market order should be at next best price");
    }

    private Trade verifyTradeExecution(String expectedBuyBroker, String expectedSellBroker, 
                                     double expectedPrice, int expectedQuantity) {
        Trade trade = tradeDataQueue.poll();
        assertNotNull(trade, "Trade should be executed");
        assertEquals(STOCK_1, trade.getStockNo(), "Trade should be for correct stock");
        assertEquals(expectedBuyBroker, trade.getBuyBrokerID(), "Buy broker should match");
        assertEquals(expectedSellBroker, trade.getSellBrokerID(), "Sell broker should match");
        assertEquals(expectedPrice, trade.getExecutedPrice(), "Execution price should match");
        assertEquals(expectedQuantity, trade.getExecutedQty(), "Execution quantity should match");
        return trade;
    }
}