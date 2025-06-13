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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import util.FileChannelService;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MockSpyTest {
    private static final ConcurrentHashMap<String, Order> orderObjMapper = new ConcurrentHashMap<>();
    private static final Logger log = LogManager.getLogger(MockSpyTest.class);
    private final LinkedBlockingQueue<Order> orderQueue = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<MarketData> marketDataQueue = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<Trade> tradeDataQueue = new LinkedBlockingQueue<>();
    OrderProcessingJob orderProcessingJob;
    LimitOrderMatchingJob orderMatching;
    int noOfStocks = 2;
    Map<String, OrderBook> orderBooks = new HashMap<>();
    @Mock
    private FIXTradeServerApp fixTradeServerApp;

    @BeforeEach
    protected void setUp() {
//        resultingTradeJob = Mockito.spy(new ResultingTradeJob(tradeDataQueue,fixTradeServerApp,fileChannelService));
        for (int i = 1; i <= noOfStocks; i++) {
            orderBooks.put(String.format("%05d", i), new OrderBook(String.format("%05d", i), "Stock " + 1));
        }
        orderProcessingJob = new OrderProcessingJob(orderQueue, orderBooks, orderObjMapper);

        OrderBook orderBook = orderBooks.get("00001");
        orderMatching = new LimitOrderMatchingJob(orderBook, orderObjMapper, marketDataQueue, tradeDataQueue);
        Order bidOrder1 = RandomOrderRequestGenerator.getNewLimitOrder("00001", "Broker 1",
                "001", "B", 8.1, 300);
        orderProcessingJob.putOrder(bidOrder1);

        Order askOrder1 = RandomOrderRequestGenerator.getNewLimitOrder("00001", "Broker 2",
                "002", "S", 8.1, 300);
        orderProcessingJob.putOrder(askOrder1);
    }

	/**
	 * Test method for verifying the behavior of the Mockito spy doReturn functionality.
	 * <p>
	 * This test method ensures that the specified return value is returned by the mocked method when it is invoked.
	 * It sets up the Mockito spy for the FileChannelService and mocks the behavior of the writeTradeToFile method to
	 * @throws IOException if an I/O error occurs
	 * @throws InterruptedException if a thread is interrupted while waiting
	 **/
    @Test
    public void testSpyDoReturn() throws IOException, InterruptedException {
		FileChannelService fileChannelService = Mockito.spy(new FileChannelService());
        // Inject the spy into the resultingTradeJob
        ResultingTradeJob resultingTradeJob = new ResultingTradeJob(tradeDataQueue,fixTradeServerApp,fileChannelService);

        doReturn(100).when(fileChannelService).writeTradeToFile(any(Trade.class), any(Path.class));
        verify(fileChannelService, never()).writeTradeToFile(any(), any());

        orderMatching.matchTopOrder();
        Trade testTrade = tradeDataQueue.take();
        resultingTradeJob.processTradeData(testTrade);
        verify(fileChannelService, times(1)).writeTradeToFile(any(Trade.class), any(Path.class));
    }

	/**
	 * Test method for verifying the behavior of a specific scenario using Mockito spy and when/thenReturn functionalities.
	 * <p>
	 * This test case validates the spy mechanism on the FileChannelService class by mocking the behavior of the
	 * writeTradeToFile method to return a specific value when invoked. It also verifies writeTradeToFile is called in stubbing.
	 *
	 * @throws IOException if an I/O error occurs
	 * @throws InterruptedException if a thread is interrupted while waiting
	 */
    @Test
    public void testSpyWhen() throws IOException, InterruptedException {
		FileChannelService fileChannelService = Mockito.spy(new FileChannelService());
        // Inject the spy into the resultingTradeJob
        ResultingTradeJob resultingTradeJob = new ResultingTradeJob(tradeDataQueue,fixTradeServerApp,fileChannelService);
        try {
            assertThrows(NullPointerException.class, (Executable) when(fileChannelService.writeTradeToFile(any(Trade.class), any(Path.class))).thenReturn(100));
        } catch (Exception _) {
        }
		// When the method writeTradeToFile is called, null values are passed.
        verify(fileChannelService).writeTradeToFile(isNull(), isNull());
        orderMatching.matchTopOrder();
        Trade testTrade = tradeDataQueue.take();
        resultingTradeJob.processTradeData(testTrade);
        verify(fileChannelService, times(1)).writeTradeToFile(any(Trade.class), any(Path.class));
    }


    /**
     * By mocking a class, it won't call method. So, it won't throw exception when calling when().thenReturn()
     * @throws IOException if an I/O error occurs
     * @throws InterruptedException if a thread is interrupted while waiting
     */
    @Test
    public void testMockWhen() throws IOException, InterruptedException {
		FileChannelService fileChannelService = Mockito.mock(FileChannelService.class);
        // Inject the mock into the resultingTradeJob
        ResultingTradeJob resultingTradeJob = new ResultingTradeJob(tradeDataQueue,fixTradeServerApp,fileChannelService);

        when(fileChannelService.writeTradeToFile(any(Trade.class), any(Path.class))).thenReturn(100);
        orderMatching.matchTopOrder();
        Trade testTrade = tradeDataQueue.take();
        resultingTradeJob.processTradeData(testTrade);
        verify(fileChannelService, times(1)).writeTradeToFile(any(Trade.class), any(Path.class));

    }

}
