package equity.orderprocessing;

import equity.client.RandomOrderRequestGenerator;
import equity.objectpooling.*;
import equity.objectpooling.Order.Action;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Spy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Limit Order Matching Tests")
public class TestOrderMatching {
    
    // Constants
    private static final String STOCK_1 = "00001";
    private static final String STOCK_2 = "00002";
    private static final String STOCK_EMPTY = "00000";
    private static final String BROKER_1 = "Broker 1";
    private static final String BROKER_2 = "Broker 2";
    private static final String BROKER_3 = "Broker 3";
    private static final String BROKER_4 = "Broker 4";
    private static final String CLIENT_BUY_INITIAL_ORDER_1 = "B001";
    private static final String CLIENT_BUY_ORDER_2 = "B002";
    private static final String CLIENT_BUY_ORDER_3 = "B003";
    private static final String CLIENT_BUY_ORDER_4 = "B004";
    private static final String CLIENT_SELL_INITIAL_ORDER_1 = "S001";
    private static final String CLIENT_SELL_ORDER_2 = "S002";
    private static final String CLIENT_SELL_ORDER_3 = "S003";
    private static final String CLIENT_SELL_ORDER_4 = "S004";
    private static final String CLIENT_BUY_ORDER_5 = "005";
    private static final String BUY = Action.BUY.value;
    private static final String SELL = Action.SELL.value;
    private static final BigDecimal PRICE_8_1 = BigDecimal.valueOf(8.1).setScale(4, RoundingMode.HALF_UP);;
    private static final BigDecimal PRICE_8_2 = BigDecimal.valueOf(8.2).setScale(4, RoundingMode.HALF_UP);;
    private static final BigDecimal PRICE_8_3 = BigDecimal.valueOf(8.3).setScale(4, RoundingMode.HALF_UP);;
    private static final BigDecimal PRICE_8_5 = BigDecimal.valueOf(8.5).setScale(4, RoundingMode.HALF_UP);;
    private static final BigDecimal PRICE_8_6 = BigDecimal.valueOf(8.6).setScale(4, RoundingMode.HALF_UP);;
    private static final int QUANTITY_100 = 100;
    private static final int QUANTITY_200 = 200;
    private static final int QUANTITY_300 = 300;
    private static final int QUANTITY_400 = 400;
    private static final int QUANTITY_1000 = 1000;
    private static final int NO_OF_STOCKS = 2;
    
    // Test data structures
    private static final ConcurrentHashMap<String, Order> orderObjMapper = new ConcurrentHashMap<>();
    
    @Mock
    private final LinkedBlockingQueue<Order> orderQueue = new LinkedBlockingQueue<>();
    
    @Spy
    private LinkedBlockingQueue<MarketData> marketDataQueue = new LinkedBlockingQueue<>();
    
    @Spy
    private LinkedBlockingQueue<Trade> tradeDataQueue = new LinkedBlockingQueue<>();
    
    // Test subjects
    private OrderProcessingJob orderProcessingJob;
    private LimitOrderMatchingJob orderMatching;
    private Map<String, OrderBook> orderBooks;

    @BeforeEach
    void setUp() {
        initializeOrderBooks();
        initializeTestSubjects();
        setupInitialOrderBook();
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
        // Add empty order book for testing
        orderBooks.put(STOCK_EMPTY, new OrderBook(STOCK_EMPTY, "Empty Stock"));
    }

    private void initializeTestSubjects() {
        orderProcessingJob = new OrderProcessingJob(orderQueue, orderBooks, orderObjMapper);
        OrderBook orderBook = orderBooks.get(STOCK_1);
        orderMatching = new LimitOrderMatchingJob(orderBook, orderObjMapper, marketDataQueue, tradeDataQueue, orderProcessingJob);
    }

    private void setupInitialOrderBook() {
        // Create limit orders for testing
        Order bidOrder1 = createLimitOrder(STOCK_1, BROKER_1, CLIENT_BUY_INITIAL_ORDER_1, BUY, PRICE_8_1, QUANTITY_300);
        Order askOrder1 = createLimitOrder(STOCK_1, BROKER_2, CLIENT_SELL_INITIAL_ORDER_1, SELL, PRICE_8_2, QUANTITY_200);
        
        orderProcessingJob.putOrder(bidOrder1);
        orderProcessingJob.putOrder(askOrder1);
    }

    private Order createLimitOrder(String stockNo, String broker, String clientOrderId,
                                   String side, BigDecimal price, int quantity) {
        return RandomOrderRequestGenerator.getNewLimitOrder(stockNo, broker, clientOrderId, side, price, quantity);
    }

    private void verifyInitialSetup() {
        assertEquals(0, PRICE_8_1.compareTo(orderBooks.get(STOCK_1).getBestBid()));
        assertEquals(0, PRICE_8_2.compareTo(orderBooks.get(STOCK_1).getBestAsk()));
        assertEquals(2, orderObjMapper.size());
    }

    private void clearTestData() {
        orderObjMapper.clear();
        marketDataQueue.clear();
        tradeDataQueue.clear();
    }

    private void clearObjectPools() {
        OrderPoolManager.clearObjects(STOCK_1);
        OrderPoolManager.clearObjects(STOCK_2);
        OrderPoolManager.clearObjects(STOCK_EMPTY);
    }

    @Test
    @DisplayName("Should handle partial fills across multiple matching cycles")
    void testPartialFill() throws InterruptedException {
        // Given - add a large ask order that will partially fill against bid
        Order largeBidOrder = createLimitOrder(STOCK_1, BROKER_3, CLIENT_BUY_ORDER_2, BUY, PRICE_8_5, QUANTITY_1000);
        orderProcessingJob.putOrder(largeBidOrder);
        
        // When - execute first matching cycle
        MatchingResult firstCycle = executeMatchingCycle();
        
        // Then - verify the first partial fill
        verifyFirstCycle(firstCycle);
        
        // Given - add another ask order for the second cycle
        Order askOrder2 = createLimitOrder(STOCK_1, BROKER_4, CLIENT_SELL_ORDER_2, SELL, PRICE_8_3, QUANTITY_400);
        orderProcessingJob.putOrder(askOrder2);
        
        // When - execute the second matching cycle
        MatchingResult secondCycle = executeMatchingCycle();
        
        // Then - verify the second partial fill
        verifySecondCycle(secondCycle);
    }

    private void verifyFirstCycle(MatchingResult result) {
        assertNotNull(result.trade);
        assertNotNull(result.marketData);
        
        assertEquals(0, PRICE_8_2.compareTo(result.trade.getExecutedPrice()));
        assertEquals(QUANTITY_200, result.trade.getExecutedQty());
        assertEquals(BROKER_3, result.trade.getBuyBrokerID());
        assertEquals(BROKER_2, result.trade.getSellBrokerID());
        
        // Verify remaining bid order
        assertEquals(0, PRICE_8_5.compareTo(result.marketData.bestBid()));
        assertNull(result.marketData.bestAsk());
        assertEquals(0, PRICE_8_2.compareTo(result.marketData.lastTradePrice()));
    }

    private void verifySecondCycle(MatchingResult result) {
        assertNotNull(result.trade);
        assertNotNull(result.marketData);
        
        assertEquals(0, PRICE_8_3.compareTo(result.trade.getExecutedPrice()));
        assertEquals(QUANTITY_400, result.trade.getExecutedQty());
        assertEquals(BROKER_3, result.trade.getBuyBrokerID());
        assertEquals(BROKER_4, result.trade.getSellBrokerID());
        
        // Verify remaining quantities
        assertEquals(0, PRICE_8_5.compareTo(result.marketData.bestBid()));
        assertNull(result.marketData.bestAsk());
        assertEquals(0, PRICE_8_3.compareTo(result.marketData.lastTradePrice()));
    }

    @Test
    @DisplayName("Should handle empty order book gracefully")
    void testEmptyBook() throws InterruptedException {
        // Given - empty order book
        LimitOrderMatchingJob emptyMatching = new LimitOrderMatchingJob(
                orderBooks.get(STOCK_EMPTY), 
                orderObjMapper, 
                marketDataQueue, 
                tradeDataQueue,
                orderProcessingJob
        );
        
        // When - attempt to match on empty book
        emptyMatching.matchTopOrder();
        
        // Then - no trades or market data should be generated
        assertTrue(marketDataQueue.isEmpty());
        assertTrue(tradeDataQueue.isEmpty());
        assertNull(orderBooks.get(STOCK_EMPTY).getBestBid());
        assertNull(orderBooks.get(STOCK_EMPTY).getBestAsk());
    }

    @Test
    @DisplayName("Should handle order book with only bid orders")
    void testEmptyAskQueue() throws InterruptedException {
        // Given - order book with only bids
        OrderBook bidOnlyBook = createOrderBookWithBidOnly();
        LimitOrderMatchingJob bidOnlyMatching = new LimitOrderMatchingJob(
                bidOnlyBook, 
                orderObjMapper, 
                marketDataQueue, 
                tradeDataQueue,
                orderProcessingJob
        );
        
        // When - attempt to match with no asks
        bidOnlyMatching.matchTopOrder();
        
        // Then - no trades should occur
        assertTrue(marketDataQueue.isEmpty());
        assertTrue(tradeDataQueue.isEmpty());
        assertEquals(0, PRICE_8_1.compareTo(bidOnlyBook.getBestBid()));
        assertNull(bidOnlyBook.getBestAsk());
    }

    @Test
    @DisplayName("Should handle order book with only ask orders")
    void testEmptyBidQueue() throws InterruptedException {
        // Given - order book with only asks
        OrderBook askOnlyBook = createOrderBookWithAskOnly();
        LimitOrderMatchingJob askOnlyMatching = new LimitOrderMatchingJob(
                askOnlyBook, 
                orderObjMapper, 
                marketDataQueue, 
                tradeDataQueue,
                orderProcessingJob
        );
        
        // When - attempt to match with no bids
        askOnlyMatching.matchTopOrder();
        
        // Then - no trades should occur
        assertTrue(marketDataQueue.isEmpty());
        assertTrue(tradeDataQueue.isEmpty());
        assertNull(askOnlyBook.getBestBid());
        assertEquals(0, PRICE_8_2.compareTo(askOnlyBook.getBestAsk()));
    }

    @Test
    @DisplayName("Should handle large bid order consuming multiple ask levels")
    void testBigBidOrder() throws InterruptedException {
        // Given - multiple ask orders at different price levels
        Order askOrder2 = createLimitOrder(STOCK_1, BROKER_2, CLIENT_SELL_ORDER_3, SELL, PRICE_8_5, QUANTITY_300);
        Order askOrder3 = createLimitOrder(STOCK_1, BROKER_2, CLIENT_SELL_ORDER_4, SELL, PRICE_8_6, QUANTITY_400);
        
        orderProcessingJob.putOrder(askOrder2);
        orderProcessingJob.putOrder(askOrder3);
        
        // Add large bid order that will consume multiple ask levels
        Order largeBidOrder = createLimitOrder(STOCK_1, BROKER_1, CLIENT_BUY_ORDER_5, BUY, PRICE_8_6, QUANTITY_1000);
        orderProcessingJob.putOrder(largeBidOrder);
        
        // When & Then - execute three matching cycles
        MatchingResult cycle1 = executeMatchingCycle();
        verifyLargeBidCycle1(cycle1);
        
        MatchingResult cycle2 = executeMatchingCycle();
        verifyLargeBidCycle2(cycle2);
        
        MatchingResult cycle3 = executeMatchingCycle();
        verifyLargeBidCycle3(cycle3);
    }

    private void verifyLargeBidCycle1(MatchingResult result) {
        assertEquals(0, PRICE_8_2.compareTo(result.trade.getExecutedPrice()));
        assertEquals(QUANTITY_200, result.trade.getExecutedQty());
        assertEquals(0, PRICE_8_6.compareTo(result.marketData.bestBid()));
        assertEquals(0, PRICE_8_5.compareTo(result.marketData.bestAsk()));
    }

    private void verifyLargeBidCycle2(MatchingResult result) {
        assertEquals(0, PRICE_8_5.compareTo(result.trade.getExecutedPrice()));
        assertEquals(QUANTITY_300, result.trade.getExecutedQty());
        assertEquals(0, PRICE_8_6.compareTo(result.marketData.bestBid()));
        assertEquals(0, PRICE_8_6.compareTo(result.marketData.bestAsk()));
    }

    private void verifyLargeBidCycle3(MatchingResult result) {
        assertEquals(0, PRICE_8_6.compareTo(result.trade.getExecutedPrice()));
        assertEquals(QUANTITY_400, result.trade.getExecutedQty());
        assertEquals(0, PRICE_8_6.compareTo(result.marketData.bestBid()));
        assertNull(result.marketData.bestAsk());
    }

    @Test
    @DisplayName("Should remain bid and ask orders after match although they looks equal")
    void testComplicatedCase() throws InterruptedException {
        // Given - clear existing orders and create a perfect match scenario
        clearTestData();
        
        Order bidOrder = createLimitOrder(STOCK_1, BROKER_1, CLIENT_BUY_ORDER_2, BUY, PRICE_8_2, QUANTITY_200);
        Order askOrder = createLimitOrder(STOCK_1, BROKER_2, CLIENT_SELL_ORDER_2, SELL, PRICE_8_1, QUANTITY_300);
        
        orderProcessingJob.putOrder(bidOrder);
        orderProcessingJob.putOrder(askOrder);
        
        // When & Then - execute two matching cycles
        MatchingResult cycle1 = executeMatchingCycle();
        MatchingResult cycle2 = executeMatchingCycle();
        MatchingResult cycle3 = executeMatchingCycle();

        // Then - verify perfect match
        assertNotNull(cycle1.trade);
        assertNotNull(cycle1.marketData);
        assertEquals(0, PRICE_8_1.compareTo(cycle1.trade.getExecutedPrice()));
        assertEquals(QUANTITY_200, cycle1.trade.getExecutedQty());
        assertEquals(CLIENT_BUY_ORDER_2, cycle1.trade.getBuyOrderID());
        assertEquals(CLIENT_SELL_ORDER_2, cycle1.trade.getSellOrderID());

        assertNotNull(cycle2.trade);
        assertNotNull(cycle2.marketData);
        assertEquals(0, PRICE_8_1.compareTo(cycle2.trade.getExecutedPrice()));
        assertEquals(QUANTITY_100, cycle2.trade.getExecutedQty());
        assertEquals(CLIENT_BUY_INITIAL_ORDER_1, cycle2.trade.getBuyOrderID());
        assertEquals(0, PRICE_8_1.compareTo(cycle2.marketData.bestBid()));
        assertEquals(0, PRICE_8_2.compareTo(cycle2.marketData.bestAsk()));

        assertNull(cycle3.trade);
        assertNull(cycle3.marketData);
    }

    private MatchingResult executeMatchingCycle() throws InterruptedException {
        orderMatching.matchTopOrder();
        MarketData marketData = marketDataQueue.poll();
        Trade trade = tradeDataQueue.poll();
        return new MatchingResult(marketData, trade);
    }

    private OrderBook createOrderBookWithBidOnly() {
        OrderBook orderBook = new OrderBook(STOCK_2, "Bid Only Stock");
        Order bidOrder = createLimitOrder(STOCK_2, BROKER_1, CLIENT_BUY_ORDER_3, BUY, PRICE_8_1, QUANTITY_300);
        
        LinkedList<Order> bidQueue = new LinkedList<>();
        bidQueue.add(bidOrder);
        orderBook.getBidMap().put(PRICE_8_1, bidQueue);
        
        return orderBook;
    }

    private OrderBook createOrderBookWithAskOnly() {
        OrderBook orderBook = new OrderBook(STOCK_2, "Ask Only Stock");
        Order askOrder = createLimitOrder(STOCK_2, BROKER_2, CLIENT_BUY_ORDER_4, SELL, PRICE_8_2, QUANTITY_200);
        
        LinkedList<Order> askQueue = new LinkedList<>();
        askQueue.add(askOrder);
        orderBook.getAskMap().put(PRICE_8_2, askQueue);
        
        return orderBook;
    }

    /**
     * Helper class to encapsulate matching results for easier verification
     */
    private static class MatchingResult {
        final MarketData marketData;
        final Trade trade;
        
        MatchingResult(MarketData marketData, Trade trade) {
            this.marketData = marketData;
            this.trade = trade;
        }
    }
}