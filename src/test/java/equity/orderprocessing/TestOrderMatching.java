package equity.orderprocessing;

import equity.client.RandomOrderRequestGenerator;
import equity.objectpooling.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Spy;

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
    private static final String CLIENT_ORDER_1 = "001";
    private static final String CLIENT_ORDER_2 = "002";
    private static final String CLIENT_ORDER_3 = "003";
    private static final String CLIENT_ORDER_4 = "004";
    private static final String CLIENT_ORDER_5 = "005";
    private static final double PRICE_8_1 = 8.1;
    private static final double PRICE_8_2 = 8.2;
    private static final double PRICE_8_5 = 8.5;
    private static final double PRICE_8_6 = 8.6;
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
    }

    private void initializeTestSubjects() {
        orderProcessingJob = new OrderProcessingJob(orderQueue, orderBooks, orderObjMapper);
        OrderBook orderBook = orderBooks.get(STOCK_1);
        orderMatching = new LimitOrderMatchingJob(orderBook, orderObjMapper, marketDataQueue, tradeDataQueue);
    }

    private void setupInitialOrderBook() {
        // Setup bid orders
        Order bidOrder1 = createLimitOrder(STOCK_1, BROKER_1, CLIENT_ORDER_1, "B", PRICE_8_5, QUANTITY_300);
        Order bidOrder2 = createLimitOrder(STOCK_1, BROKER_1, CLIENT_ORDER_2, "B", PRICE_8_1, QUANTITY_100);
        
        // Setup ask orders
        Order askOrder1 = createLimitOrder(STOCK_1, BROKER_2, CLIENT_ORDER_2, "S", PRICE_8_2, QUANTITY_100);
        Order askOrder2 = createLimitOrder(STOCK_1, BROKER_2, CLIENT_ORDER_3, "S", PRICE_8_5, QUANTITY_300);
        Order askOrder3 = createLimitOrder(STOCK_1, BROKER_2, CLIENT_ORDER_4, "S", PRICE_8_5, QUANTITY_400);
        
        orderProcessingJob.putOrder(bidOrder1);
        orderProcessingJob.putOrder(bidOrder2);
        orderProcessingJob.putOrder(askOrder1);
        orderProcessingJob.putOrder(askOrder2);
        orderProcessingJob.putOrder(askOrder3);
    }

    private Order createLimitOrder(String stockNo, String broker, String clientOrderId, 
                                 String side, double price, int quantity) {
        return RandomOrderRequestGenerator.getNewLimitOrder(stockNo, broker, clientOrderId, side, price, quantity);
    }

    private void verifyInitialSetup() {
        assertEquals(0, OrderPoolManager.getFreeOrderCount(STOCK_1));
        assertEquals(5, OrderPoolManager.getUsedOrderCount(STOCK_1));
    }

    private void clearTestData() {
        orderObjMapper.clear();
        marketDataQueue.clear();
        tradeDataQueue.clear();
    }

    private void clearObjectPools() {
        OrderPoolManager.clearObjects(STOCK_1);
        OrderPoolManager.clearObjects(STOCK_2);
    }

    @Test
    @DisplayName("Should handle partial fills across multiple matching cycles")
    void testPartialFill() throws InterruptedException {
        // First matching cycle
        MatchingResult firstCycle = executeMatchingCycle();
        verifyFirstCycle(firstCycle);
        
        // Second matching cycle
        MatchingResult secondCycle = executeMatchingCycle();
        verifySecondCycle(secondCycle);
        
        // Third matching cycle - no more matches
        orderMatching.matchTopOrder();
        assertNull(marketDataQueue.poll(), "No market data should be generated when no match occurs");
        assertNull(tradeDataQueue.poll(), "No trade should be executed when no match occurs");
        assertEquals(2, OrderPoolManager.getFreeOrderCount(STOCK_1), "Free count should remain unchanged");
    }

    private void verifyFirstCycle(MatchingResult result) {
        // Verify market data
        assertEquals(PRICE_8_5, result.marketData.bestBid(), "Best bid should remain at 8.5");
        assertEquals(PRICE_8_5, result.marketData.bestAsk(), "Best ask should be 8.5 after consuming 8.2 ask");
        assertEquals(PRICE_8_2, result.marketData.lastTradePrice(), "Trade should execute at ask price");
        
        // Verify trade
        assertEquals(PRICE_8_2, result.trade.getExecutedPrice(), "Trade price should be 8.2");
        assertEquals(QUANTITY_100, result.trade.getExecutedQty(), "Trade quantity should be 100");
        
        // Verify object pools
        assertEquals(1, OrderPoolManager.getFreeOrderCount(STOCK_1), "One order should be freed");
        assertEquals(4, OrderPoolManager.getUsedOrderCount(STOCK_1), "Four orders should remain active");
    }

    private void verifySecondCycle(MatchingResult result) {
        // Verify market data
        assertEquals(PRICE_8_1, result.marketData.bestBid(), "Best bid should drop to 8.1");
        assertEquals(PRICE_8_5, result.marketData.bestAsk(), "Best ask should remain at 8.5");
        assertEquals(PRICE_8_5, result.marketData.lastTradePrice(), "Trade should execute at ask price");
        
        // Verify order book structure
        assertEquals(1, result.marketData.bidMap().size(), "Should have one bid price level");
        assertEquals(2, result.marketData.askMap().get(PRICE_8_5).size(), "Should have two orders at 8.5 ask");
        
        // Verify remaining order quantity
        Order remainingOrder = result.marketData.askMap().get(PRICE_8_5).peek();
        assertNotNull(remainingOrder, "Should have remaining order");
        assertEquals(QUANTITY_100, remainingOrder.getQuantity().get(), "Remaining order should have 100 quantity");
        
        // Verify trade details
        assertEquals(PRICE_8_5, result.trade.getExecutedPrice(), "Trade price should be 8.5");
        assertEquals(QUANTITY_200, result.trade.getExecutedQty(), "Trade quantity should be 200");
        
        // Verify object pools
        assertEquals(2, OrderPoolManager.getFreeOrderCount(STOCK_1), "Two orders should be freed");
        assertEquals(3, OrderPoolManager.getUsedOrderCount(STOCK_1), "Three orders should remain active");
    }

    @Test
    @DisplayName("Should handle empty order book gracefully")
    void testEmptyBook() throws InterruptedException {
        // Given - empty order book
        OrderBook emptyOrderBook = new OrderBook(STOCK_EMPTY, "Empty Stock");
        LimitOrderMatchingJob emptyMatching = new LimitOrderMatchingJob(
                emptyOrderBook, orderObjMapper, marketDataQueue, tradeDataQueue);
        
        // When - attempt matching on empty book
        emptyMatching.matchTopOrder();
        
        // Then - no market data or trades should be generated
        assertNull(marketDataQueue.poll(), "No market data should be generated for empty book");
        assertNull(tradeDataQueue.poll(), "No trades should be generated for empty book");
    }

    @Test
    @DisplayName("Should handle order book with only bid orders")
    void testEmptyAskQueue() throws InterruptedException {
        // Given - order book with only bid order
        OrderBook bidOnlyBook = createOrderBookWithBidOnly();
        LimitOrderMatchingJob bidOnlyMatching = new LimitOrderMatchingJob(
                bidOnlyBook, orderObjMapper, marketDataQueue, tradeDataQueue);
        
        // When - attempt matching
        bidOnlyMatching.matchTopOrder();
        
        // Then - no matching should occur
        assertNull(marketDataQueue.poll(), "No market data should be generated with only bids");
        assertNull(tradeDataQueue.poll(), "No trades should be generated with only bids");
    }

    @Test
    @DisplayName("Should handle order book with only ask orders")
    void testEmptyBidQueue() throws InterruptedException {
        // Given - order book with only ask order  
        OrderBook askOnlyBook = createOrderBookWithAskOnly();
        LimitOrderMatchingJob askOnlyMatching = new LimitOrderMatchingJob(
                askOnlyBook, orderObjMapper, marketDataQueue, tradeDataQueue);
        
        // When - attempt matching
        askOnlyMatching.matchTopOrder();
        
        // Then - no matching should occur
        assertNull(marketDataQueue.poll(), "No market data should be generated with only asks");
        assertNull(tradeDataQueue.poll(), "No trades should be generated with only asks");
    }

    @Test
    @DisplayName("Should handle large bid order consuming multiple ask levels")
    void testBigBidOrder() throws InterruptedException {
        // Given - large bid order
        Order largeBidOrder = createLimitOrder(STOCK_1, BROKER_1, CLIENT_ORDER_5, "B", PRICE_8_6, QUANTITY_1000);
        orderProcessingJob.putOrder(largeBidOrder);
        
        // Execute three matching cycles to consume all asks
        MatchingResult cycle1 = executeMatchingCycle();
        MatchingResult cycle2 = executeMatchingCycle();
        MatchingResult cycle3 = executeMatchingCycle();
        
        // Verify first cycle - consumes 8.2 ask
        verifyLargeBidCycle1(cycle1);
        
        // Verify second cycle - partially consumes first 8.5 ask
        verifyLargeBidCycle2(cycle2);
        
        // Verify third cycle - consumes remaining 8.5 ask
        verifyLargeBidCycle3(cycle3);
        
        // Verify final object pool state
        assertEquals(3, OrderPoolManager.getFreeOrderCount(STOCK_1), "Three orders should be freed");
        assertEquals(3, OrderPoolManager.getUsedOrderCount(STOCK_1), "Three orders should remain active");
    }

    private void verifyLargeBidCycle1(MatchingResult result) {
        assertEquals(PRICE_8_6, result.marketData.bestBid(), "Best bid should be 8.6");
        assertEquals(PRICE_8_5, result.marketData.bestAsk(), "Best ask should advance to 8.5");
        assertEquals(PRICE_8_2, result.marketData.lastTradePrice(), "Should trade at 8.2");
        assertEquals(QUANTITY_100, result.trade.getExecutedQty(), "Should execute 100 shares");
    }

    private void verifyLargeBidCycle2(MatchingResult result) {
        assertEquals(PRICE_8_6, result.marketData.bestBid(), "Best bid should remain 8.6");
        assertEquals(PRICE_8_5, result.marketData.bestAsk(), "Best ask should remain 8.5");
        assertEquals(PRICE_8_5, result.marketData.lastTradePrice(), "Should trade at 8.5");
        assertEquals(QUANTITY_300, result.trade.getExecutedQty(), "Should execute 300 shares");
        assertEquals(CLIENT_ORDER_5, result.trade.getBuyOrderID(), "Buy order should be the large bid");
        assertEquals(CLIENT_ORDER_3, result.trade.getSellOrderID(), "Sell order should be first 8.5 ask");
    }

    private void verifyLargeBidCycle3(MatchingResult result) {
        assertEquals(PRICE_8_6, result.marketData.bestBid(), "Best bid should remain 8.6");
        assertNull(result.marketData.bestAsk(), "All asks should be consumed");
        assertEquals(PRICE_8_5, result.marketData.lastTradePrice(), "Should trade at 8.5");
        assertEquals(QUANTITY_400, result.trade.getExecutedQty(), "Should execute 400 shares");
        assertEquals(CLIENT_ORDER_5, result.trade.getBuyOrderID(), "Buy order should be the large bid");
        assertEquals(CLIENT_ORDER_4, result.trade.getSellOrderID(), "Sell order should be second 8.5 ask");
    }

    @Test
    @DisplayName("Should handle perfect match with complete order fulfillment")
    void testPerfectMatch() throws InterruptedException {
        // Given - clean order book for stock 2
        OrderBook stock2OrderBook = orderBooks.get(STOCK_2);
        LimitOrderMatchingJob stock2Matching = new LimitOrderMatchingJob(
                stock2OrderBook, orderObjMapper, marketDataQueue, tradeDataQueue);
        
        // Add matching bid and ask orders
        Order bidOrder = createLimitOrder(STOCK_2, BROKER_1, CLIENT_ORDER_1, "B", PRICE_8_1, QUANTITY_100);
        Order askOrder = createLimitOrder(STOCK_2, BROKER_2, CLIENT_ORDER_2, "S", PRICE_8_1, QUANTITY_100);
        
        orderProcessingJob.putOrder(bidOrder);
        orderProcessingJob.putOrder(askOrder);
        
        // When - execute matching
        stock2Matching.matchTopOrder();
        
        // Then - verify complete match
        MarketData marketData = marketDataQueue.poll();
        assertNotNull(marketData, "Market data should be generated");
        assertNull(marketData.bestBid(), "No remaining bids");
        assertNull(marketData.bestAsk(), "No remaining asks");
        assertEquals(PRICE_8_1, marketData.lastTradePrice(), "Trade price should be 8.1");
        
        Trade trade = tradeDataQueue.poll();
        assertNotNull(trade, "Trade should be executed");
        assertEquals(STOCK_2, trade.getStockNo(), "Trade should be for stock 2");
        assertEquals(PRICE_8_1, trade.getExecutedPrice(), "Trade price should be 8.1");
        assertEquals(QUANTITY_100, trade.getExecutedQty(), "Full quantity should be executed");
        
        // Verify order books are empty
        assertTrue(stock2OrderBook.getAskMap().isEmpty(), "Ask map should be empty");
        assertTrue(stock2OrderBook.getBidMap().isEmpty(), "Bid map should be empty");
        
        // Verify object pools
        assertEquals(2, OrderPoolManager.getFreeOrderCount(STOCK_2), "Both orders should be freed");
        assertEquals(0, OrderPoolManager.getUsedOrderCount(STOCK_2), "No orders should remain active");
        assertEquals(5, OrderPoolManager.getUsedOrderCount(STOCK_1), "Stock 1 orders should be unchanged");
    }

    private MatchingResult executeMatchingCycle() throws InterruptedException {
        orderMatching.matchTopOrder();
        MarketData marketData = marketDataQueue.poll();
        Trade trade = tradeDataQueue.poll();
        assertNotNull(marketData, "Market data should be generated");
        assertNotNull(trade, "Trade should be executed");
        return new MatchingResult(marketData, trade);
    }

    private OrderBook createOrderBookWithBidOnly() {
        OrderBook orderBook = new OrderBook(STOCK_EMPTY, "Bid Only Stock");
        Order bidOrder = createLimitOrder(STOCK_EMPTY, BROKER_1, CLIENT_ORDER_1, "B", PRICE_8_2, QUANTITY_100);
        orderObjMapper.put(bidOrder.getBrokerID() + "-" + bidOrder.getClientOrdID(), bidOrder);
        
        LinkedList<Order> orderList = new LinkedList<>();
        orderList.add(bidOrder);
        orderBook.getBidMap().put(bidOrder.getPrice().get(), orderList);
        return orderBook;
    }

    private OrderBook createOrderBookWithAskOnly() {
        OrderBook orderBook = new OrderBook(STOCK_EMPTY, "Ask Only Stock");
        Order askOrder = createLimitOrder(STOCK_EMPTY, BROKER_1, CLIENT_ORDER_1, "S", PRICE_8_2, QUANTITY_100);
        orderObjMapper.put(askOrder.getBrokerID() + "-" + askOrder.getClientOrdID(), askOrder);
        
        LinkedList<Order> orderList = new LinkedList<>();
        orderList.add(askOrder);
        orderBook.getAskMap().put(askOrder.getPrice().get(), orderList);
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