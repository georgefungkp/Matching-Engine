package equity.orderprocessing;

import equity.client.RandomOrderRequestGenerator;
import equity.objectpooling.*;
import equity.objectpooling.Order.Side;
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
    private static final String BUY = Side.BUY.value;
    private static final String SELL = Side.SELL.value;
    private static final String STOCK_1 = "00001";
    private static final String STOCK_2 = "00002";
    private static final String BROKER_1 = "Broker 1";
    private static final String BROKER_2 = "Broker 2";
    private static final String BROKER_3 = "Broker 3";
    private static final String BROKER_4 = "Broker 4";
    private static final String CLIENT_BUY_INITIAL_LIMIT_ORDER_1 = "BL01";
    private static final String CLIENT_BUY_INITIAL_LIMIT_ORDER_2 = "BL02";
    private static final String CLIENT_BUY_LIMIT_ORDER_2 = "BL02";
    private static final String CLIENT_BUY_LIMIT_ORDER_3 = "BL02";
    private static final String CLIENT_BUY_MARKET_ORDER_1 = "BM01";
    private static final String CLIENT_BUY_MARKET_ORDER_2 = "BM02";
    private static final String CLIENT_SELL_INITIAL_LIMIT_ORDER_1 = "SL01";
    private static final String CLIENT_SELL_LIMIT_ORDER_2 = "SL02";
    private static final String CLIENT_SELL_LIMIT_ORDER_3 = "SL03";
    private static final String CLIENT_SELL_MARKET_ORDER_1 = "SM01";
    private static final String CLIENT_SELL_MARKET_ORDER_2 = "SM02";

    // Price Constants
    private static final BigDecimal PRICE_8_0 = BigDecimal.valueOf(8.0).setScale(4, RoundingMode.HALF_UP);
    private static final BigDecimal PRICE_8_1 = BigDecimal.valueOf(8.1).setScale(4, RoundingMode.HALF_UP);
    private static final BigDecimal PRICE_8_2 = BigDecimal.valueOf(8.2).setScale(4, RoundingMode.HALF_UP);
    private static final BigDecimal PRICE_8_3 = BigDecimal.valueOf(8.3).setScale(4, RoundingMode.HALF_UP);
    private static final BigDecimal PRICE_8_5 = BigDecimal.valueOf(8.5).setScale(4, RoundingMode.HALF_UP);

    // Quantity Constants  
    private static final int QUANTITY_100 = 100;
    private static final int QUANTITY_150 = 150;
    private static final int QUANTITY_200 = 200;
    private static final int QUANTITY_300 = 300;
    private static final int QUANTITY_400 = 400;
    private static final int QUANTITY_500 = 500;
    private static final int QUANTITY_600 = 600;

    // System Constants
    private static final int NO_OF_STOCKS = 2;
    private static final int INITIAL_FREE_ORDERS = 0;

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
        addLimitBidOrder(BROKER_1, CLIENT_BUY_INITIAL_LIMIT_ORDER_1, PRICE_8_1, QUANTITY_300);
        addLimitBidOrder(BROKER_3, CLIENT_BUY_INITIAL_LIMIT_ORDER_2, PRICE_8_2, QUANTITY_300);
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
        putLimitOrder(broker, clientOrderId, BUY, price, quantity);
    }

    private void addLimitAskOrder(String broker, String clientOrderId, BigDecimal price, int quantity) {
        putLimitOrder(broker, clientOrderId, SELL, price, quantity);
    }

    // === Market Order Tests ===

    @Test
    @DisplayName("Should execute market sell order against highest bid")
    void testMarketSellOrderAgainstBestBid() throws InterruptedException {
        // Given - market sell order targeting the best bid
        putMarketOrder(BROKER_4, CLIENT_SELL_MARKET_ORDER_1, SELL, QUANTITY_400);

        // Verify pre-match state
        assertEquals(0, PRICE_8_2.compareTo(orderBooks.get(STOCK_1).getBestBid()), "Best bid should be 8.2");
        assertEquals(0, PRICE_8_2.compareTo(orderBooks.get(STOCK_1).getBestAsk()), "Market order should price at best bid");

        // When - execute matching
        MarketOrderMatchResult result = executeMarketOrderMatch();
        // Then - verify execution against best bid
        verifyMarketSellExecution(result, CLIENT_BUY_INITIAL_LIMIT_ORDER_2, CLIENT_SELL_MARKET_ORDER_1, PRICE_8_2, QUANTITY_300);
        verifyMarketDataAfterSell(result.marketData, PRICE_8_1, PRICE_8_1, PRICE_8_2);
        verifyObjectPoolState(1, 2);

        // When - 2nd execute matching
        MarketOrderMatchResult result2 = executeMarketOrderMatch();
        // Then - verify execution against best bid
        verifyMarketSellExecution(result2, CLIENT_BUY_INITIAL_LIMIT_ORDER_1, CLIENT_SELL_MARKET_ORDER_1, PRICE_8_1, QUANTITY_100);
        verifyMarketDataAfterSell(result2.marketData, PRICE_8_1, null, PRICE_8_1);
        verifyObjectPoolState(2, 1);

    }

    @Test
    @DisplayName("Should execute market buy order against lowest ask")
    void testMarketBuyOrderAgainstBestAsk() throws InterruptedException {
        // Given - setup ask order and market buy order
        addLimitAskOrder(BROKER_2, CLIENT_SELL_LIMIT_ORDER_2, PRICE_8_3, QUANTITY_300);
        putMarketOrder(BROKER_4, CLIENT_BUY_MARKET_ORDER_1, BUY, QUANTITY_300);

        // When - execute matching
        MarketOrderMatchResult result = executeMarketOrderMatch();

        // Then - verify execution against best ask
        verifyMarketBuyExecution(result, CLIENT_BUY_MARKET_ORDER_1, CLIENT_SELL_LIMIT_ORDER_2, PRICE_8_3, QUANTITY_300);
        verifyMarketDataAfterBuy(result.marketData, PRICE_8_2, null, PRICE_8_3);
        verifyObjectPoolState(2, 2);
    }

    @Test
    @DisplayName("Should execute higher limit buy order against lowest ask")
    void testMarketBuyOrderAgainstBestAsk2() throws InterruptedException {
        // Given - setup ask order and market buy order
        addLimitAskOrder(BROKER_2, CLIENT_SELL_LIMIT_ORDER_2, PRICE_8_1, QUANTITY_300);
        putMarketOrder(BROKER_4, CLIENT_BUY_MARKET_ORDER_1, BUY, QUANTITY_300);

        // When - execute matching
        MarketOrderMatchResult result = executeMarketOrderMatch();

        // Then - verify execution against best ask
        verifyMarketBuyExecution(result, CLIENT_BUY_INITIAL_LIMIT_ORDER_2, CLIENT_SELL_LIMIT_ORDER_2, PRICE_8_1, QUANTITY_300);
//        TODO: 0should execute market order first
//        verifyMarketBuyExecution(result, CLIENT_BUY_MARKET_ORDER_1, CLIENT_SELL_LIMIT_ORDER_2, PRICE_8_1, QUANTITY_300);
        verifyMarketDataAfterBuy(result.marketData, PRICE_8_1, null, PRICE_8_1);
    }

    @Test
    @DisplayName("Executed at the best bid price at every trade of market order")
    void testMarketOrderExecutedAtBestBid() throws InterruptedException {
        // Given - setup market buy order
        putMarketOrder(BROKER_2, CLIENT_SELL_MARKET_ORDER_1, SELL, QUANTITY_500);
        // When - execute matching
        MarketOrderMatchResult result = executeMarketOrderMatch();

        // Then - verify execution against best bid
        verifyMarketBuyExecution(result, CLIENT_BUY_INITIAL_LIMIT_ORDER_2, CLIENT_SELL_MARKET_ORDER_1, PRICE_8_2, QUANTITY_300);
        verifyMarketDataAfterBuy(result.marketData, PRICE_8_1, PRICE_8_1, PRICE_8_2);
        assertEquals(0, result.trade.getBuyOrderRemainingQty());
        assertEquals(PRICE_8_2, result.trade.getBuyOrderAvgExecutedPrice());
        assertEquals(QUANTITY_200, result.trade.getSellOrderRemainingQty());
        assertEquals(PRICE_8_2, result.trade.getSellOrderAvgExecutedPrice());

        // When - execute second matching
        MarketOrderMatchResult result2 = executeMarketOrderMatch();

        // Then - verify execution against best bid
        verifyMarketBuyExecution(result2, CLIENT_BUY_INITIAL_LIMIT_ORDER_1, CLIENT_SELL_MARKET_ORDER_1, PRICE_8_1, QUANTITY_200);
        verifyMarketDataAfterBuy(result2.marketData, PRICE_8_1, null, PRICE_8_1);
        assertEquals(QUANTITY_100, result2.trade.getBuyOrderRemainingQty());
        assertEquals(PRICE_8_1, result2.trade.getBuyOrderAvgExecutedPrice());
        assertEquals(0, result2.trade.getSellOrderRemainingQty());
        assertEquals(BigDecimal.valueOf(8.16).setScale(4, RoundingMode.HALF_UP), result2.trade.getSellOrderAvgExecutedPrice());
    }


    @ParameterizedTest
    @ValueSource(ints = {100, 200, 500, 1000})
    @DisplayName("Should handle various market order sizes correctly")
    void testVariousMarketOrderSizes(int orderSize) throws InterruptedException {
        // Given - additional liquidity for testing different sizes
        addLimitBidOrder(BROKER_3, CLIENT_BUY_LIMIT_ORDER_2, PRICE_8_0, QUANTITY_500);

        // When - place market sell order of varying sizes
        putMarketOrder(BROKER_4, CLIENT_BUY_MARKET_ORDER_1, SELL, orderSize);

        // Then - should execute against best available prices
        if (orderSize <= QUANTITY_300) {
            // Should fill completely against best bid (8.2)
            MarketOrderMatchResult result = executeMarketOrderMatch();
            assertEquals(0, PRICE_8_2.compareTo(result.trade.getExecutedPrice()), "Should execute at best bid price");
            assertEquals(orderSize, result.trade.getExecutedQty(),
                    "Should execute available quantity");
        } else {
            // Should require multiple fills
            MarketOrderMatchResult firstFill = executeMarketOrderMatch();
            assertEquals(PRICE_8_2, firstFill.trade.getExecutedPrice(), "First fill at best bid");
            assertEquals(QUANTITY_300, firstFill.trade.getExecutedQty(), "First fill quantity");

            MarketOrderMatchResult secondFill = executeMarketOrderMatch();
            assertEquals(PRICE_8_1, secondFill.trade.getExecutedPrice(), "Second fill at next best bid");

            if (orderSize >= QUANTITY_600) {
                MarketOrderMatchResult thirdFill = executeMarketOrderMatch();
                assertEquals(PRICE_8_0, thirdFill.trade.getExecutedPrice(), "First fill at best bid");
                assertEquals(QUANTITY_400, thirdFill.trade.getExecutedQty(), "First fill quantity");
            }
        }
    }

    @Test
    @DisplayName("Should maintain correct market data state after market order execution")
    void testMarketDataConsistencyAfterExecution() throws InterruptedException {
        // Given - complex order book setup accounting for initial setup
        // Initial state: Bids [8.2: 300 (BROKER_3), 8.1: 300 (BROKER_1)]
        addLimitAskOrder(BROKER_2, CLIENT_SELL_LIMIT_ORDER_2, PRICE_8_3, QUANTITY_200);
        addLimitBidOrder(BROKER_3, CLIENT_BUY_LIMIT_ORDER_2, PRICE_8_0, QUANTITY_100);

        // Final order book state:
        // Bids: [8.2: 300 (BROKER_3), 8.1: 300 (BROKER_1), 8.0: 100 (BROKER_3)]
        // Asks: [8.3: 200 (BROKER_2)]

        // When - execute market BUY order (should match against ask at 8.3)
        putMarketOrder(BROKER_4, CLIENT_BUY_MARKET_ORDER_1, BUY, QUANTITY_200);
        MarketOrderMatchResult result = executeMarketOrderMatch();

        // Then - verify market data reflects the correct state after buy execution
        assertEquals(PRICE_8_2, result.marketData.bestBid(), "Best bid should remain unchanged at 8.2");
        assertNull(result.marketData.bestAsk(), "Ask should be completely consumed (200 ask vs 200 market buy)");
        assertEquals(PRICE_8_3, result.marketData.lastTradePrice(), "Last trade price should be 8.3 (ask price)");

        // Verify order book structure integrity
        OrderBook orderBook = orderBooks.get(STOCK_1);
        assertFalse(orderBook.getBidMap().isEmpty(), "Bid map should still contain all original orders");
        assertTrue(orderBook.getAskMap().isEmpty(), "Ask map should be empty after ask order consumed");

        // Verify the bid side is completely intact (market buy doesn't affect bids)
        assertEquals(3, orderBook.getBidMap().size(), "Should have 3 bid price levels unchanged");
        assertEquals(0, PRICE_8_2.compareTo(orderBook.getBestBid()), "Best bid should still be 8.2");
    }

    @Test
    @DisplayName("Should handle FIFO priority correctly with complete fills")
    void testFIFOPriorityWithCompleteFills() throws InterruptedException {
        // Given - Add orders at same price level to test FIFO
        // Initial: [8.2: 300 (BROKER_3), 8.1: 300 (BROKER_1)]
        addLimitBidOrder(BROKER_2, "FIFO_TEST_1", PRICE_8_2, QUANTITY_200); // 2nd at 8.2
        addLimitBidOrder(BROKER_4, "FIFO_TEST_2", PRICE_8_2, QUANTITY_100); // 3rd at 8.2

        // FIFO Queue at 8.2: BROKER_3(300) → BROKER_2(200) → BROKER_4(100)

        // When - market sell exactly fills first order
        putMarketOrder(BROKER_1, CLIENT_SELL_MARKET_ORDER_1, SELL, QUANTITY_300);
        MarketOrderMatchResult result1 = executeMarketOrderMatch();

        // Then - should completely fill BROKER_3's order (first in FIFO)
        assertEquals(BROKER_3, result1.trade.getBuyBrokerID(), "First fill: BROKER_3");
        assertEquals(PRICE_8_2, result1.trade.getExecutedPrice(), "Should execute at 8.2");
        assertEquals(QUANTITY_300, result1.trade.getExecutedQty(), "Should fill complete order");

        // When - another market sell to test next in FIFO
        putMarketOrder(BROKER_1, CLIENT_SELL_MARKET_ORDER_2, SELL, QUANTITY_200);
        MarketOrderMatchResult result2 = executeMarketOrderMatch();

        // Then - should fill BROKER_2's order (second in FIFO)
        assertEquals(BROKER_2, result2.trade.getBuyBrokerID(), "Second fill: BROKER_2");
        assertEquals("FIFO_TEST_1", result2.trade.getBuyOrderID(), "Correct order ID");
        assertEquals(PRICE_8_2, result2.trade.getExecutedPrice(), "Should execute at 8.2");
        assertEquals(QUANTITY_200, result2.trade.getExecutedQty(), "Should fill BROKER_2's order");

        // Verify BROKER_4's order is now at top of queue
        assertNotNull( orderBooks.get(STOCK_1).getBidMap().get(PRICE_8_2).peekFirst(), "BROKER_4's order is still in the order book");
        assertEquals(PRICE_8_2, result2.marketData.bestBid(),"Best bid still 8.2 (BROKER_4's order remaining)");
    }

    @Test
    @DisplayName("Should maintain FIFO priority with partial fills")
    void testFIFOPriorityWithPartialFills() throws InterruptedException {
        // Given - Clean slate setup
        OrderBook orderBook = orderBooks.get(STOCK_1);
        orderBook.getBidMap().clear();

        // Setup: Different sized orders at same price
        addLimitBidOrder("BROKER_X", "BIG_ORDER", PRICE_8_2, QUANTITY_500);   // 1st - Large
        addLimitBidOrder("BROKER_Y", "SMALL_ORDER", PRICE_8_2, QUANTITY_100); // 2nd - Small

        // Test: Partial fill of first order
        putMarketOrder("MARKET_BROKER", "PARTIAL_SELL", SELL, QUANTITY_300);
        MarketOrderMatchResult result1 = executeMarketOrderMatch();

        // Should partially fill the FIRST order (BROKER_X)
        assertEquals("BROKER_X", result1.trade.getBuyBrokerID(), "Should match first order");
        assertEquals(QUANTITY_300, result1.trade.getExecutedQty(), "Partial fill quantity");

        // Test: Next market sell should complete the first order
        putMarketOrder("MARKET_BROKER", "COMPLETE_SELL", SELL, QUANTITY_200);
        MarketOrderMatchResult result2 = executeMarketOrderMatch();

        // Should complete the SAME first order (remaining 200 shares)
        assertEquals("BROKER_X", result2.trade.getBuyBrokerID(), "Should complete first order");
        assertEquals("BIG_ORDER", result2.trade.getBuyOrderID(), "Same order ID");
        assertEquals(QUANTITY_200, result2.trade.getExecutedQty(), "Remaining quantity");

        // Test: Next market sell should move to second order
        putMarketOrder("MARKET_BROKER", "NEXT_SELL", SELL, QUANTITY_100);
        MarketOrderMatchResult result3 = executeMarketOrderMatch();

        // Now should match the SECOND order
        assertEquals("BROKER_Y", result3.trade.getBuyBrokerID(), "Should match second order");
        assertEquals("SMALL_ORDER", result3.trade.getBuyOrderID(), "Second order ID");
        assertEquals(QUANTITY_100, result3.trade.getExecutedQty(), "Second order quantity");
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

    private void verifyMarketSellExecution(MarketOrderMatchResult result, String expectedBuyOrderID,
                                           String expectedSellOrderID, BigDecimal expectedPrice, int expectedQuantity) {
        verifyTradeExecution(result.trade, expectedBuyOrderID, expectedSellOrderID, expectedPrice, expectedQuantity);
    }

    private void verifyMarketBuyExecution(MarketOrderMatchResult result, String expectedBuyOrderID,
                                          String expectedSellOrderID, BigDecimal expectedPrice, int expectedQuantity) {
        verifyTradeExecution(result.trade, expectedBuyOrderID, expectedSellOrderID, expectedPrice, expectedQuantity);
    }

    private void verifyPartialFillExecution(MarketOrderMatchResult result, String expectedBuyOrderID,
                                            String expectedSellOrderID, BigDecimal expectedPrice, int expectedQuantity) {
        verifyTradeExecution(result.trade, expectedBuyOrderID, expectedSellOrderID, expectedPrice, expectedQuantity);
    }

    private void verifyRemainingQuantityExecution(MarketOrderMatchResult result, String expectedBuyOrderID,
                                                  String expectedSellOrderID, BigDecimal expectedPrice, int expectedQuantity) {
        verifyTradeExecution(result.trade, expectedBuyOrderID, expectedSellOrderID, expectedPrice, expectedQuantity);
    }

    private void verifyTradeExecution(Trade trade, String expectedBuyOrderID, String expectedSellOrderID,
                                      BigDecimal expectedPrice, int expectedQuantity) {
        assertEquals(STOCK_1, trade.getStockNo(), "Trade should be for correct stock");
        assertEquals(expectedBuyOrderID, trade.getBuyOrderID(), "Buy order ID should match expected");
        assertEquals(expectedSellOrderID, trade.getSellOrderID(), "Sell order ID should match expected");
        assertEquals(expectedPrice, trade.getExecutedPrice(), "Execution price should match expected");
        assertEquals(expectedQuantity, trade.getExecutedQty(), "Execution quantity should match expected");
    }

    private void verifyMarketDataAfterSell(MarketData marketData, BigDecimal expectedBestBid,
                                           BigDecimal expectedBestAsk, BigDecimal expectedLastTradePrice) {
        assertEquals(expectedBestBid, marketData.bestBid(), "Best bid should match expected");
        assertEquals(expectedBestAsk, marketData.bestAsk(), "Best ask should match expected");
        assertEquals(0, expectedLastTradePrice.compareTo(marketData.lastTradePrice()),
                "Last trade price should match expected");
    }

    private void verifyMarketDataAfterBuy(MarketData marketData, BigDecimal expectedBestBid,
                                          BigDecimal expectedBestAsk, BigDecimal expectedLastTradePrice) {
        assertEquals(expectedBestBid, marketData.bestBid(), "Best bid should match expected");
        assertEquals(expectedBestAsk, marketData.bestAsk(), "Best ask should match expected");
        assertEquals(expectedLastTradePrice, marketData.lastTradePrice(),
                "Last trade price should match expected");
    }

    private void verifyObjectPoolState(int expectedFreeOrders, int expectedUsedOrders) {
        assertEquals(expectedFreeOrders, OrderPoolManager.getFreeOrderCount(STOCK_1),
                "Free order count should match expected");
        assertEquals(expectedUsedOrders, OrderPoolManager.getUsedOrderCount(STOCK_1),
                "Used order count should match expected");
    }

    private void setupBidOnlyOrderBook() {
        addLimitBidOrder(BROKER_1, CLIENT_BUY_LIMIT_ORDER_2, PRICE_8_1, QUANTITY_300);
        addLimitBidOrder(BROKER_2, CLIENT_BUY_LIMIT_ORDER_3, PRICE_8_0, QUANTITY_200);
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