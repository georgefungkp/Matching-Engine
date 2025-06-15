package equity.orderprocessing;

import equity.client.RandomOrderRequestGenerator;
import equity.objectpooling.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Market Order Matching Tests")
public class TestMarketOrderMatching {
    
    // Test Constants
    private static final String STOCK_1 = "00001";
    private static final String STOCK_2 = "00002";
    private static final String BROKER_1 = "Broker 1";
    private static final String BROKER_2 = "Broker 2";
    private static final String BROKER_3 = "Broker 3";
    private static final String BROKER_4 = "Broker 4";
    private static final String CLIENT_ORDER_1 = "001";
    private static final String CLIENT_ORDER_2 = "002";
    private static final String CLIENT_ORDER_3 = "003";
    
    // Price Constants
    private static final BigDecimal PRICE_8_0 = BigDecimal.valueOf(8.0).setScale(4, RoundingMode.HALF_UP);;
    private static final BigDecimal PRICE_8_1 = BigDecimal.valueOf(8.1).setScale(4, RoundingMode.HALF_UP);;
    private static final BigDecimal PRICE_8_2 = BigDecimal.valueOf(8.2).setScale(4, RoundingMode.HALF_UP);;
    private static final BigDecimal PRICE_8_3 = BigDecimal.valueOf(8.3).setScale(4, RoundingMode.HALF_UP);;
    private static final BigDecimal PRICE_8_5 = BigDecimal.valueOf(8.5).setScale(4, RoundingMode.HALF_UP);;
    
    // Quantity Constants  
    private static final int QUANTITY_100 = 100;
    private static final int QUANTITY_150 = 150;
    private static final int QUANTITY_200 = 200;
    private static final int QUANTITY_300 = 300;
    private static final int QUANTITY_400 = 400;
    private static final int QUANTITY_500 = 500;
    
    // System Constants
    private static final int NO_OF_STOCKS = 2;
    private static final int INITIAL_FREE_ORDERS = 0;
    private static final int EXPECTED_POOL_INCREASE = 1;
    
    // Test Infrastructure
    private static final ConcurrentHashMap<String, Order> orderObjMapper = new ConcurrentHashMap<>();
    private final LinkedBlockingQueue<Order> orderQueue = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<MarketData> marketDataQueue = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<Trade> tradeDataQueue = new LinkedBlockingQueue<>();
    
    // Test Subjects
    private OrderProcessingJob orderProcessingJob;
    private LimitOrderMatchingJob orderMatching;
    private Map<String, OrderBook> orderBooks;

    @BeforeEach
    void setUp() {
        initializeTestEnvironment();
        setupInitialOrderBook();
        verifyInitialState();
    }

    @AfterEach
    void tearDown() {
        cleanupTestEnvironment();
    }

    // === Initialization Methods ===

    private void initializeTestEnvironment() {
        initializeOrderBooks();
        initializeTestSubjects();
    }

    private void initializeOrderBooks() {
        orderBooks = new HashMap<>();
        for (int i = 1; i <= NO_OF_STOCKS; i++) {
            String stockId = String.format("%05d", i);
            String stockName = "Stock " + i;
            orderBooks.put(stockId, new OrderBook(stockId, stockName));
        }
    }

    private void initializeTestSubjects() {
        orderProcessingJob = new OrderProcessingJob(orderQueue, orderBooks, orderObjMapper);
        OrderBook orderBook = orderBooks.get(STOCK_1);
        orderMatching = new LimitOrderMatchingJob(orderBook, orderObjMapper, marketDataQueue, tradeDataQueue, orderProcessingJob);
    }

    private void setupInitialOrderBook() {
        // Create bid orders at different price levels to provide liquidity
        addLimitBidOrder(BROKER_1, CLIENT_ORDER_1, PRICE_8_1, QUANTITY_300);
        addLimitBidOrder(BROKER_3, CLIENT_ORDER_2, PRICE_8_2, QUANTITY_300);
    }

    private void verifyInitialState() {
        assertEquals(INITIAL_FREE_ORDERS, OrderPoolManager.getFreeOrderCount(STOCK_1),
                "Initial free order count should be zero");
        assertEquals(2, OrderPoolManager.getUsedOrderCount(STOCK_1),
                "Should have exactly 2 used orders after setup");
    }

    private void cleanupTestEnvironment() {
        clearDataStructures();
        clearObjectPools();
    }

    private void clearDataStructures() {
        orderObjMapper.clear();
        marketDataQueue.clear();
        tradeDataQueue.clear();
    }

    private void clearObjectPools() {
        OrderPoolManager.clearObjects(STOCK_1);
        OrderPoolManager.clearObjects(STOCK_2);
    }

    // === Order Creation Helper Methods ===

    private void putLimitOrder(String broker, String clientOrderId, String side, BigDecimal price, int quantity) {
        Order order = RandomOrderRequestGenerator.getNewLimitOrder(STOCK_1, broker, clientOrderId, side, price, quantity);
        orderProcessingJob.putOrder(order);
    }

    private void putMarketOrder(String broker, String clientOrderId, String side, int quantity) {
        Order order = RandomOrderRequestGenerator.getNewMarketOrder(STOCK_1, broker, clientOrderId, side, quantity);
        orderProcessingJob.putOrder(order);
    }

    private void addLimitBidOrder(String broker, String clientOrderId, BigDecimal price, int quantity) {
        putLimitOrder(broker, clientOrderId, "B", price, quantity);
    }

    private void addLimitAskOrder(String broker, String clientOrderId, BigDecimal price, int quantity) {
        putLimitOrder(broker, clientOrderId, "S", price, quantity);
    }

    // === Market Order Tests ===

    @Test
    @DisplayName("Should execute market sell order against highest bid")
    void testMarketSellOrderAgainstBestBid() throws InterruptedException {
        // Given - market sell order targeting the best bid
        putMarketOrder(BROKER_4, CLIENT_ORDER_1, "S", QUANTITY_400);
        
        // Verify pre-match state
        assertEquals(0, PRICE_8_2.compareTo(orderBooks.get(STOCK_1).getBestBid()), "Best bid should be 8.2");
        assertEquals(0, PRICE_8_2.compareTo(orderBooks.get(STOCK_1).getBestAsk()), "Market order should price at best bid");
        
        // When - execute matching
        MarketOrderMatchResult result = executeMarketOrderMatch();
        
        // Then - verify execution against best bid
        verifyMarketSellExecution(result, BROKER_3, BROKER_4, PRICE_8_2, QUANTITY_300);
        verifyMarketDataAfterSell(result.marketData, PRICE_8_1, PRICE_8_2, PRICE_8_2);
        verifyObjectPoolState(EXPECTED_POOL_INCREASE, 4);
        // When - 2nd matching
        MarketOrderMatchResult result2 = executeMarketOrderMatch();
        verifyMarketSellExecution(result, BROKER_3, BROKER_4, PRICE_8_1, QUANTITY_300);
        verifyMarketDataAfterSell(result.marketData, PRICE_8_1, PRICE_8_2, PRICE_8_2);
    }

    @Test
    @DisplayName("Should execute market buy order against lowest ask")
    void testMarketBuyOrderAgainstBestAsk() throws InterruptedException {
        // Given - setup ask order and market buy order
        addLimitAskOrder(BROKER_2, CLIENT_ORDER_1, PRICE_8_1, QUANTITY_300);
        putMarketOrder(BROKER_4, CLIENT_ORDER_2, "B", QUANTITY_300);
        
        // When - execute matching
        MarketOrderMatchResult result = executeMarketOrderMatch();
        
        // Then - verify execution against best ask
        verifyMarketBuyExecution(result, BROKER_4, BROKER_2, PRICE_8_1, QUANTITY_300);
        verifyMarketDataAfterBuy(result.marketData, PRICE_8_2, null, PRICE_8_1);
        verifyObjectPoolState(EXPECTED_POOL_INCREASE, 3);
    }

    @Test
    @DisplayName("Should handle market order partial fill scenario")
    void testMarketOrderPartialFill() throws InterruptedException {
        // Given - market order larger than available liquidity
        putMarketOrder(BROKER_4, CLIENT_ORDER_1, "S", QUANTITY_400);
        
        // When - execute first matching cycle
        MarketOrderMatchResult firstMatch = executeMarketOrderMatch();
        
        // Then - verify partial execution
        verifyPartialFillExecution(firstMatch, BROKER_3, BROKER_4, PRICE_8_2, QUANTITY_300);
        
        // Verify remaining market order repositioned to next best price
        assertEquals(0, PRICE_8_1.compareTo(firstMatch.marketData.bestBid()), "Next best bid should be visible");
        assertEquals(0, PRICE_8_1.compareTo(firstMatch.marketData.bestAsk()), "Remaining market order should price at 8.1");
        
        // When - execute second matching cycle for remaining quantity
        MarketOrderMatchResult secondMatch = executeMarketOrderMatch();
        
        // Then - verify completion of market order
        verifyRemainingQuantityExecution(secondMatch, BROKER_1, BROKER_4, PRICE_8_1, QUANTITY_100);
    }

    @Test
    @DisplayName("Should reject market order when no opposite side liquidity exists")
    void testMarketOrderWithNoOppositeLiquidity() {
        // Given - clear existing setup and add only bid orders
        clearDataStructures();
        setupBidOnlyOrderBook();
        
        // When - attempt to add market sell order with no asks
        int initialOrderCount = OrderPoolManager.getUsedOrderCount(STOCK_1);
        putMarketOrder(BROKER_4, CLIENT_ORDER_1, "S", QUANTITY_300);
        
        // Then - market order should be rejected/not added
        assertEquals(initialOrderCount, OrderPoolManager.getUsedOrderCount(STOCK_1),
                "No new orders should be added when no opposite liquidity exists");
        assertTrue(marketDataQueue.isEmpty(), "No market data should be generated");
        assertTrue(tradeDataQueue.isEmpty(), "No trades should be executed");
    }

    @ParameterizedTest
    @ValueSource(ints = {100, 200, 500, 1000})
    @DisplayName("Should handle various market order sizes correctly")
    void testVariousMarketOrderSizes(int orderSize) throws InterruptedException {
        // Given - additional liquidity for testing different sizes
        addLimitBidOrder(BROKER_3, CLIENT_ORDER_3, PRICE_8_0, QUANTITY_500);
        
        // When - place market sell order of varying sizes
        putMarketOrder(BROKER_4, CLIENT_ORDER_1, "S", orderSize);
        
        // Then - should execute against best available prices
        if (orderSize <= QUANTITY_300) {
            // Should fill completely against best bid (8.2)
            MarketOrderMatchResult result = executeMarketOrderMatch();
            assertEquals(0, PRICE_8_2.compareTo(result.trade.getExecutedPrice()), "Should execute at best bid price");
            assertEquals(Math.min(orderSize, QUANTITY_300), result.trade.getExecutedQty(),
                    "Should execute available quantity");
        } else {
            // Should require multiple fills
            MarketOrderMatchResult firstFill = executeMarketOrderMatch();
            assertEquals(0, PRICE_8_2.compareTo(firstFill.trade.getExecutedPrice()), "First fill at best bid");
            assertEquals(QUANTITY_300, firstFill.trade.getExecutedQty(), "First fill quantity");
            
            if (orderSize > QUANTITY_300) {
                MarketOrderMatchResult secondFill = executeMarketOrderMatch();
                assertEquals(0, PRICE_8_1.compareTo(secondFill.trade.getExecutedPrice()), "Second fill at next best bid");
            }
        }
    }

    @Test
    @DisplayName("Should maintain correct market data state after market order execution")
    void testMarketDataConsistencyAfterExecution() throws InterruptedException {
        // Given - complex order book setup
        addLimitAskOrder(BROKER_2, CLIENT_ORDER_1, PRICE_8_3, QUANTITY_200);
        addLimitBidOrder(BROKER_3, CLIENT_ORDER_3, PRICE_8_0, QUANTITY_100);
        
        // When - execute market buy order
        putMarketOrder(BROKER_4, CLIENT_ORDER_2, "B", QUANTITY_200);
        MarketOrderMatchResult result = executeMarketOrderMatch();
        
        // Then - verify market data reflects correct state
        assertEquals(0, PRICE_8_2.compareTo(result.marketData.bestBid()), "Best bid should be preserved");
        assertNull(result.marketData.bestAsk(), "Ask should be consumed completely");
        assertEquals(0, PRICE_8_3.compareTo(result.marketData.lastTradePrice()), "Last trade price should match execution");
        
        // Verify order book structure integrity
        OrderBook orderBook = orderBooks.get(STOCK_1);
        assertFalse(orderBook.getBidMap().isEmpty(), "Bid map should still contain orders");
        assertTrue(orderBook.getAskMap().isEmpty(), "Ask map should be empty after consumption");
    }

    @Test
    @DisplayName("Should handle market order timing and priority correctly")
    void testMarketOrderTimingAndPriority() throws InterruptedException {
        // Given - setup with multiple orders at same price level
        addLimitBidOrder(BROKER_2, "100", PRICE_8_2, QUANTITY_100); // Second order at 8.2
        addLimitBidOrder(BROKER_3, "101", PRICE_8_2, QUANTITY_100); // Third order at 8.2
        
        // When - execute market sell order
        putMarketOrder(BROKER_4, CLIENT_ORDER_1, "S", QUANTITY_150);
        MarketOrderMatchResult result = executeMarketOrderMatch();
        
        // Then - should execute against first order in FIFO order
        assertEquals(BROKER_3, result.trade.getBuyBrokerID(), "Should match against first order at price level");
        assertEquals(QUANTITY_100, result.trade.getExecutedQty(), "Should fill first order completely");
        
        // Verify remaining quantity creates new market order
        assertEquals(0, PRICE_8_2.compareTo(result.marketData.bestBid()), "Best bid should remain at 8.2");
        assertEquals(0, PRICE_8_2.compareTo(result.marketData.bestAsk()), "Remaining market order should be at 8.2");
    }

    // === Helper Methods for Verification ===

    private MarketOrderMatchResult executeMarketOrderMatch() throws InterruptedException {
        orderMatching.matchTopOrder();
        MarketData marketData = marketDataQueue.poll();
        Trade trade = tradeDataQueue.poll();
        assertNotNull(marketData, "Market data should be generated after matching");
        assertNotNull(trade, "Trade should be executed after matching");
        return new MarketOrderMatchResult(marketData, trade);
    }

    private void verifyMarketSellExecution(MarketOrderMatchResult result, String expectedBuyBroker,
                                         String expectedSellBroker, BigDecimal expectedPrice, int expectedQuantity) {
        verifyTradeExecution(result.trade, expectedBuyBroker, expectedSellBroker, expectedPrice, expectedQuantity);
    }

    private void verifyMarketBuyExecution(MarketOrderMatchResult result, String expectedBuyBroker,
                                        String expectedSellBroker, BigDecimal expectedPrice, int expectedQuantity) {
        verifyTradeExecution(result.trade, expectedBuyBroker, expectedSellBroker, expectedPrice, expectedQuantity);
    }

    private void verifyPartialFillExecution(MarketOrderMatchResult result, String expectedBuyBroker,
                                          String expectedSellBroker, BigDecimal expectedPrice, int expectedQuantity) {
        verifyTradeExecution(result.trade, expectedBuyBroker, expectedSellBroker, expectedPrice, expectedQuantity);
    }

    private void verifyRemainingQuantityExecution(MarketOrderMatchResult result, String expectedBuyBroker,
                                                String expectedSellBroker, BigDecimal expectedPrice, int expectedQuantity) {
        verifyTradeExecution(result.trade, expectedBuyBroker, expectedSellBroker, expectedPrice, expectedQuantity);
    }

    private void verifyTradeExecution(Trade trade, String expectedBuyBroker, String expectedSellBroker,
                                    BigDecimal expectedPrice, int expectedQuantity) {
        assertEquals(STOCK_1, trade.getStockNo(), "Trade should be for correct stock");
        assertEquals(expectedBuyBroker, trade.getBuyBrokerID(), "Buy broker should match expected");
        assertEquals(expectedSellBroker, trade.getSellBrokerID(), "Sell broker should match expected");
        assertEquals(0, expectedPrice.compareTo(trade.getExecutedPrice()), "Execution price should match expected");
        assertEquals(expectedQuantity, trade.getExecutedQty(), "Execution quantity should match expected");
    }

    private void verifyMarketDataAfterSell(MarketData marketData, BigDecimal expectedBestBid,
                                         BigDecimal expectedBestAsk, BigDecimal expectedLastTradePrice) {
        assertEquals(0, expectedBestBid.compareTo(marketData.bestBid()), "Best bid should match expected");
        assertEquals(0, expectedBestAsk.compareTo(marketData.bestAsk()), "Best ask should match expected");
        assertEquals(0, expectedLastTradePrice.compareTo(marketData.lastTradePrice()),
                "Last trade price should match expected");
    }

    private void verifyMarketDataAfterBuy(MarketData marketData, BigDecimal expectedBestBid,
                                        BigDecimal expectedBestAsk, BigDecimal expectedLastTradePrice) {
        assertEquals(0, expectedBestBid.compareTo(marketData.bestBid()), "Best bid should match expected");
        assertEquals(expectedBestAsk, marketData.bestAsk(), "Best ask should match expected");
        assertEquals(0, expectedLastTradePrice.compareTo(marketData.lastTradePrice()),
                "Last trade price should match expected");
    }

    private void verifyObjectPoolState(int expectedFreeOrders, int expectedUsedOrders) {
        assertEquals(expectedFreeOrders, OrderPoolManager.getFreeOrderCount(STOCK_1),
                "Free order count should match expected");
        assertEquals(expectedUsedOrders, OrderPoolManager.getUsedOrderCount(STOCK_1),
                "Used order count should match expected");
    }

    private void setupBidOnlyOrderBook() {
        addLimitBidOrder(BROKER_1, CLIENT_ORDER_1, PRICE_8_1, QUANTITY_300);
        addLimitBidOrder(BROKER_2, CLIENT_ORDER_2, PRICE_8_0, QUANTITY_200);
    }

    /**
     * Helper class to encapsulate market order matching results
     */
    private static class MarketOrderMatchResult {
        final MarketData marketData;
        final Trade trade;

        MarketOrderMatchResult(MarketData marketData, Trade trade) {
            this.marketData = marketData;
            this.trade = trade;
        }
    }
}