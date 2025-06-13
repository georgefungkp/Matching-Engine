package equity.orderprocessing;

import equity.client.RandomOrderRequestGenerator;
import equity.externalparties.ResultingTradeJob;
import equity.fix.server.FIXTradeServerApp;
import equity.objectpooling.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import util.FileChannelService;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Object Pool Management Tests")
public class TestObjectPool {
    
    // Constants
    private static final String STOCK_1 = "00001";
    private static final String STOCK_2 = "00002";
    private static final String BROKER_1 = "Broker 1";
    private static final String BROKER_2 = "Broker 2";
    private static final String CLIENT_ORDER_1 = "001";
    private static final String CLIENT_ORDER_2 = "002";
    private static final double PRICE_8_1 = 8.1;
    private static final double PRICE_8_0 = 8.0;
    private static final double PRICE_8_3 = 8.3;
    private static final double PRICE_7_1 = 7.1;
    private static final int QUANTITY_300 = 300;
    private static final int QUANTITY_100 = 100;
    private static final int NO_OF_STOCKS = 2;
    
    // Test data structures
    private static final ConcurrentHashMap<String, Order> orderObjMapper = new ConcurrentHashMap<>();
    private final LinkedBlockingQueue<Order> orderQueue = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<MarketData> marketDataQueue = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<Trade> tradeDataQueue = new LinkedBlockingQueue<>();
    
    // Mock dependencies
    @Mock
    private FIXTradeServerApp fixTradeServerApp;
    
    @Mock
    private FileChannelService fileChannelService;

    @InjectMocks
    private ResultingTradeJob resultingTradeJob;

    // Test subjects
    private OrderProcessingJob orderProcessingJob;
    private LimitOrderMatchingJob orderMatching;
    private Map<String, OrderBook> orderBooks;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        initializeOrderBooks();
        initializeTestSubjects();
        setupInitialOrders();
        verifyInitialState();
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
        Order bidOrder1 = RandomOrderRequestGenerator.getNewLimitOrder(
                STOCK_1, BROKER_1, CLIENT_ORDER_1, "B", PRICE_8_1, QUANTITY_300);
        orderProcessingJob.putOrder(bidOrder1);

        Order askOrder1 = RandomOrderRequestGenerator.getNewLimitOrder(
                STOCK_1, BROKER_2, CLIENT_ORDER_2, "S", PRICE_8_1, QUANTITY_300);
        orderProcessingJob.putOrder(askOrder1);
    }

    private void verifyInitialState() {
        assertEquals(0, OrderPoolManager.getFreeOrderCount(STOCK_1));
        assertEquals(2, OrderPoolManager.getUsedOrderCount(STOCK_1));
    }

    @AfterEach
    void tearDown() {
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

    @Test
    @DisplayName("Should reuse Order objects after matching")
    void testReUseOrderObject() throws InterruptedException {
        // Given - initial state verification
        assertEquals(0, OrderPoolManager.getFreeTradeCount(STOCK_1));
        assertEquals(0, OrderPoolManager.getUsedTradeCount(STOCK_1));
        
        // When - match orders
        orderMatching.matchTopOrder();
        
        // Then - verify order objects are returned to pool
        assertEquals(2, OrderPoolManager.getFreeOrderCount(STOCK_1));
        assertEquals(0, OrderPoolManager.getUsedOrderCount(STOCK_1));
        assertEquals(0, OrderPoolManager.getFreeTradeCount(STOCK_1));
        assertEquals(1, OrderPoolManager.getUsedTradeCount(STOCK_1));

        // When - create new order for same stock
        RandomOrderRequestGenerator.getNewLimitOrder(
                STOCK_1, BROKER_2, CLIENT_ORDER_1, "S", PRICE_8_0, QUANTITY_100);
        
        // Then - verify object reuse
        assertEquals(1, OrderPoolManager.getFreeOrderCount(STOCK_1));
        assertEquals(1, OrderPoolManager.getUsedOrderCount(STOCK_1));

        // When - create order for different stock
        RandomOrderRequestGenerator.getNewLimitOrder(
                STOCK_2, BROKER_2, CLIENT_ORDER_1, "S", PRICE_8_0, QUANTITY_100);
        
        // Then - verify stock-specific pooling
        assertEquals(1, OrderPoolManager.getFreeOrderCount(STOCK_1));
        assertEquals(1, OrderPoolManager.getUsedOrderCount(STOCK_1));
        assertEquals(0, OrderPoolManager.getFreeOrderCount(STOCK_2));
        assertEquals(1, OrderPoolManager.getUsedOrderCount(STOCK_2));
    }

    @Test
    @DisplayName("Should reuse Trade objects after processing")
    void testReUseTradeObject() throws Exception {
        // Given - mock file service behavior
        when(fileChannelService.writeTradeToFile(any(Trade.class), any(Path.class)))
                .thenReturn(100);
        
        // When - match orders to create trade
        orderMatching.matchTopOrder();
        
        // Then - verify trade object creation
        assertEquals(0, OrderPoolManager.getFreeTradeCount(STOCK_1));
        assertEquals(1, OrderPoolManager.getUsedTradeCount(STOCK_1));
        
        // When - process trade
        Trade testTrade = tradeDataQueue.take();
        verify(fileChannelService, never()).writeTradeToFile(any(), any());
        
        resultingTradeJob.processTradeData(testTrade);
        
        // Then - verify trade processing and object return
        verify(fileChannelService, times(1)).writeTradeToFile(eq(testTrade), any(Path.class));
        assertEquals(1, OrderPoolManager.getFreeTradeCount(STOCK_1));
        assertEquals(0, OrderPoolManager.getUsedTradeCount(STOCK_1));

        // Given - setup for second trade
        setupSecondTradeScenario();
        
        // When - create and process second trade
        orderMatching.matchTopOrder();
        Trade testTrade2 = tradeDataQueue.take();
        resultingTradeJob.processTradeData(testTrade2);
        
        // Then - verify continued object reuse
        assertEquals(1, OrderPoolManager.getFreeTradeCount(STOCK_1));
        assertEquals(0, OrderPoolManager.getUsedTradeCount(STOCK_1));
    }

    private void setupSecondTradeScenario() {
        OrderBook orderBook = orderBooks.get(STOCK_1);
        orderMatching = new LimitOrderMatchingJob(orderBook, orderObjMapper, marketDataQueue, tradeDataQueue);
        
        Order bidOrder = RandomOrderRequestGenerator.getNewLimitOrder(
                STOCK_1, BROKER_1, CLIENT_ORDER_1, "B", PRICE_8_3, QUANTITY_300);
        orderProcessingJob.putOrder(bidOrder);

        Order askOrder = RandomOrderRequestGenerator.getNewLimitOrder(
                STOCK_1, BROKER_2, CLIENT_ORDER_2, "S", PRICE_8_3, QUANTITY_300);
        orderProcessingJob.putOrder(askOrder);
    }

    @Test
    @DisplayName("Should successfully remove orders from order book")
    void testRemoveOrder() {
        // Given - verify initial order book state
        assertNotNull(orderBooks.get(STOCK_1).getBidMap());
        assertEquals(PRICE_8_1, orderBooks.get(STOCK_1).getBestBid());
        
        // When - remove order
        boolean removed = orderProcessingJob.removeOrder(BROKER_1, CLIENT_ORDER_1);
        
        // Then - verify order removal
        assertTrue(removed);
        assertTrue(orderBooks.get(STOCK_1).getBidMap().isEmpty());
        assertNull(orderBooks.get(STOCK_1).getBestBid());
    }

    @Test
    @DisplayName("Should successfully update order price and quantity")
    void testUpdateOrder() throws InterruptedException {
        // Given - verify initial state
        assertEquals(PRICE_8_1, orderBooks.get(STOCK_1).getBestBid());
        
        // When - update order
        boolean updated = orderProcessingJob.updateOrder(BROKER_1, CLIENT_ORDER_1, PRICE_7_1, QUANTITY_100);
        
        // Then - verify order update
        assertTrue(updated);
        assertEquals(PRICE_7_1, orderBooks.get(STOCK_1).getBestBid());
        assertEquals(1, orderBooks.get(STOCK_1).getBidMap().size());
        assertEquals(1, orderBooks.get(STOCK_1).getBidMap().get(PRICE_7_1).size());
        assertEquals(QUANTITY_100, orderBooks.get(STOCK_1).getBidMap().get(PRICE_7_1).getFirst().getQuantity().get());
        
        // When - attempt matching after update
        orderMatching.matchTopOrder();
        
        // Then - verify no trade occurs due to price mismatch
        assertTrue(tradeDataQueue.isEmpty());
    }
}