package equity.orderprocessing;

import equity.client.RandomOrderRequestGenerator;
import equity.externalparties.ResultingTradeJob;
import equity.fix.server.FIXTradeServerApp;
import equity.objectpooling.MarketData;
import equity.objectpooling.Order;
import equity.objectpooling.OrderBook;
import equity.objectpooling.Trade;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import util.FileChannelService;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static util.ReadConfig.dotenv;

@ExtendWith(MockitoExtension.class)
@DisplayName("Mockito Spy and Mock Behavior Tests")
public class TestMockSpy {
    
    // Constants
    private static final String STOCK_1 = "00001";
    private static final String BROKER_1 = "Broker 1";
    private static final String BROKER_2 = "Broker 2";
    private static final String CLIENT_ORDER_1 = "001";
    private static final String CLIENT_ORDER_2 = "002";
    private static final BigDecimal ORDER_PRICE = BigDecimal.valueOf(8.1);
    private static final int ORDER_QUANTITY = 300;
    private static final int EXPECTED_WRITE_RESULT = 100;
    private static final int NO_OF_STOCKS = Integer.parseInt(Objects.requireNonNull(dotenv.get("no_of_stock")));
    
    private static final Logger log = LogManager.getLogger(TestMockSpy.class);
    
    // Test data structures
    private static final ConcurrentHashMap<String, Order> orderObjMapper = new ConcurrentHashMap<>();
    private final LinkedBlockingQueue<Order> orderQueue = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<MarketData> marketDataQueue = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<Trade> tradeDataQueue = new LinkedBlockingQueue<>();
    
    // Test subjects
    private OrderProcessingJob orderProcessingJob;
    private LimitOrderMatchingJob orderMatching;
    private Map<String, OrderBook> orderBooks;
    
    @Mock
    private FIXTradeServerApp fixTradeServerApp;

    @BeforeEach
    void setUp() {
        initializeOrderBooks();
        initializeTestSubjects();
        setupInitialOrders();
    }

    @AfterEach
    void tearDown() {
        orderObjMapper.clear();
        marketDataQueue.clear();
        tradeDataQueue.clear();
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
        orderMatching = new LimitOrderMatchingJob(orderBook, orderObjMapper, marketDataQueue, tradeDataQueue, orderProcessingJob);
    }

    private void setupInitialOrders() {
        Order bidOrder = createBidOrder();
        Order askOrder = createAskOrder();
        
        orderProcessingJob.putOrder(bidOrder);
        orderProcessingJob.putOrder(askOrder);
    }

    private Order createBidOrder() {
        return RandomOrderRequestGenerator.getNewLimitOrder(
                STOCK_1, BROKER_1, CLIENT_ORDER_1, "B", ORDER_PRICE, ORDER_QUANTITY);
    }

    private Order createAskOrder() {
        return RandomOrderRequestGenerator.getNewLimitOrder(
                STOCK_1, BROKER_2, CLIENT_ORDER_2, "S", ORDER_PRICE, ORDER_QUANTITY);
    }

    @Test
    @DisplayName("Should demonstrate spy doReturn behavior without calling real method")
    void testSpyDoReturn() throws IOException, InterruptedException {
        // Given - spy with stubbed behavior
        FileChannelService fileChannelService = spy(new FileChannelService());
        ResultingTradeJob resultingTradeJob = new ResultingTradeJob(
                tradeDataQueue, fixTradeServerApp, fileChannelService);
        
        doReturn(EXPECTED_WRITE_RESULT)
                .when(fileChannelService)
                .writeTradeToFile(any(Trade.class), any(Path.class));
        
        // Initially no interactions
        verify(fileChannelService, never()).writeTradeToFile(any(), any());

        // When - execute trade processing
        Trade trade = executeTradeMatching();
        resultingTradeJob.processTradeData(trade);
        
        // Then - verify method was called exactly once
        verify(fileChannelService, times(1))
                .writeTradeToFile(any(Trade.class), any(Path.class));
    }

    @Test
    @DisplayName("Should demonstrate spy when/thenReturn calling real method during stubbing")
    void testSpyWhen() throws IOException, InterruptedException {
        // Given - spy that will call real method during stubbing
        FileChannelService fileChannelService = spy(new FileChannelService());
        ResultingTradeJob resultingTradeJob = new ResultingTradeJob(
                tradeDataQueue, fixTradeServerApp, fileChannelService);
        
        // When - stubbing with when() calls the real method with null arguments
        try {
            assertThrows(NullPointerException.class, () -> {
                when(fileChannelService.writeTradeToFile(any(Trade.class), any(Path.class)))
                        .thenReturn(EXPECTED_WRITE_RESULT);
            });
        } catch (Exception ignored) {
            // Expected behavior - spy calls real method during stubbing
        }

        // Then - verify real method was called with null during stubbing
        verify(fileChannelService).writeTradeToFile(isNull(), isNull());
        
        // When - execute actual trade processing
        Trade trade = executeTradeMatching();
        resultingTradeJob.processTradeData(trade);
        
        // Then - verify method was called with actual trade
        verify(fileChannelService, times(1))
                .writeTradeToFile(any(Trade.class), any(Path.class));
    }

    @Test
    @DisplayName("Should demonstrate mock when/thenReturn not calling real method")
    void testMockWhen() throws IOException, InterruptedException {
        // Given - mock that won't call real method
        FileChannelService fileChannelService = mock(FileChannelService.class);
        ResultingTradeJob resultingTradeJob = new ResultingTradeJob(
                tradeDataQueue, fixTradeServerApp, fileChannelService);

        // When - stubbing with mock doesn't call real method
        when(fileChannelService.writeTradeToFile(any(Trade.class), any(Path.class)))
                .thenReturn(EXPECTED_WRITE_RESULT);
        
        // Execute trade processing
        Trade trade = executeTradeMatching();
        resultingTradeJob.processTradeData(trade);
        
        // Then - verify mocked method was called
        verify(fileChannelService, times(1))
                .writeTradeToFile(any(Trade.class), any(Path.class));
    }

    private Trade executeTradeMatching() throws InterruptedException {
        orderMatching.matchTopOrder();
        return tradeDataQueue.take();
    }
}