package equity.requesthandling;

import equity.externalparties.MarketDataJob;
import equity.externalparties.ResultingTradeJob;
import equity.fix.server.FIXTradeServerApp;
import equity.objectpooling.*;
import equity.objectpooling.Order.OrderType;
import equity.objectpooling.Order.Side;
import equity.orderprocessing.LimitOrderMatchingJob;
import equity.orderprocessing.OrderProcessingJob;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import util.FileChannelService;
import util.FileResourcesUtils;
import util.HealthCheck;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import static util.HealthCheck.*;
import static util.ReadConfig.dotenv;
import static util.ReadConfig.getStocks;


public class MatchingEngine extends Thread {
    private static final int noOfAvailableThreads = Runtime.getRuntime().availableProcessors();
    private static final Logger log = LogManager.getLogger(MatchingEngine.class);
    private static final String SUCCESS_MSG_TEMPLATE = " is processing your order: ";

    private static final LinkedBlockingQueue<Order> orderQueue = new LinkedBlockingQueue<>();
    private static final LinkedBlockingQueue<MarketData> marketDataQueue = new LinkedBlockingQueue<>();
    private static final LinkedBlockingQueue<Trade> resultingTradeQueue = new LinkedBlockingQueue<>();
    private final FIXTradeServerApp fixTradeServerApp;
    private static final HashMap<String, OrderBook> orderBooks = new HashMap<>();
    private static final ConcurrentHashMap<String, Order> orderObjMapper = new ConcurrentHashMap<>();
    private static final FileChannelService fileChannelService = new FileChannelService();
    private static final int noOfThreadsPerStock = Integer.parseInt(Objects.requireNonNull(dotenv.get("noOfThreadPerStock")));

    private boolean listening = true;


    public MatchingEngine() {
        System.out.println(System.getProperty("java.class.path"));
        fixTradeServerApp = new FIXTradeServerApp(orderQueue);
        new Thread(fixTradeServerApp).start();
        log.debug("Number of available threads in this machine: {}", noOfAvailableThreads);
    }

    public static void main(String[] args) {
        FileResourcesUtils.ensureDirectoryExists("logs");
        MatchingEngine server = new MatchingEngine();
        printJVMFlags();
        printGCStats();
        printAsyncLoggingHealthCheck();
        server.startProcessingJobs();
        server.start();
    }

    /**
     * This method initializes and starts multiple order matching jobs for the stocks based on the configured number of stocks.
     * Each stock is associated with an OrderBook that contains bID and ask order maps. A new thread is created for each OrderBook
     * and a LimitOrderMatchingJob is executed on that thread to match bID and ask orders.
     */
    private void startOrderMatchingJobs(OrderProcessingJob orderProcessingJob){
        for (String stockId: getStocks()) {
            OrderBook orderBook = new OrderBook(stockId, "Stock " + stockId);
            orderBooks.put(stockId, orderBook);

            for (int i = 1; i < noOfThreadsPerStock + 1; i++) {
                LimitOrderMatchingJob matchingJob = new LimitOrderMatchingJob(
                        orderBook,
                        orderObjMapper,
                        marketDataQueue,
                        resultingTradeQueue,
                        orderProcessingJob,
                        i
                );
                Thread matchingThread = new Thread(matchingJob);
                matchingThread.setName("Matching-" + stockId + "-Thread-" + i);
                matchingThread.start();
                log.info("Started matching thread {}", matchingThread.getName());
            }

        }
    }


    public void startProcessingJobs() {
        OrderProcessingJob orderProcessingJob = new OrderProcessingJob(orderQueue, orderBooks, orderObjMapper);
        startOrderMatchingJobs(orderProcessingJob);
        new Thread(orderProcessingJob).start();
        new Thread(new MarketDataJob(marketDataQueue,fileChannelService), "MarketData").start();
        new Thread(new ResultingTradeJob(resultingTradeQueue, this.fixTradeServerApp, fileChannelService), "TradeData").start();
    }


    /**
     * This method is responsible for setting up a ServerSocket at a specified port and continuously
     * listening for incoming client connections. Upon accepting a connection, it reads the order data
     * from the client, creates an Order object, puts the order in a queue, and sends a success message
     * back to the client. If any exceptions occur during the process, the method logs the error and
     * stops listening for further connections.
     *
     * @throws IOException if an I/O error occurs when creating the server socket or working with the streams
     */
    private void gettingOnline() throws IOException {
        // Create a ServerSocket at a certain port
        try (ServerSocket serverSocket = new ServerSocket(Integer.parseInt(Objects.requireNonNull(dotenv.get("port_number"))))) {
            while (listening) {
                // Await client connection
                log.debug("Waiting for client on port {}...", serverSocket.getLocalPort());
                Socket server = serverSocket.accept();
                log.debug("Just connected to {}", server.getRemoteSocketAddress());
                try (BufferedReader in = new BufferedReader(new InputStreamReader(server.getInputStream()));
                     DataOutputStream out = new DataOutputStream(server.getOutputStream())) {
                    Order order = createOrder(in.readLine());
                    orderQueue.put(order);
                    log.debug("Order Queue size: {}", orderQueue.size());
                    HealthCheck.checkQueueHealth(orderQueue);
                    out.writeUTF(server.getLocalSocketAddress() + SUCCESS_MSG_TEMPLATE + order.getBrokerID() + "-" + order.getClientOrdID());
                } catch (Exception e) {
                    e.printStackTrace();
                    log.error(e);
                    listening = false;
                }
                server.close();
            }
        }
    }


    /**
     * Creates an Order object based on the provIDed input value.
     *
     * @param value the string value containing order information separated by ":" in the format:
     *              "stockNo : brokerID : orderType : buyOrSell : price : quantity"
     * @return the created Order object with the parsed order details
     */
    private Order createOrder(String value) {
//        		String message = order.getStockNo() + ":" + order.getBrokerID() + ":" + order.getClientOrdID() + ":"
//				+ order.getOrderType() + ":" + order.getBuyOrSell() + ":"
//				+ order.getPrice() + ":" + order.getQuantity();
        String[] tokens = value.split(":");
        return OrderPoolManager.requestOrderObj(tokens[0], tokens[1], tokens[2], OrderType.getByValue(tokens[3]), Side.getByValue(tokens[4]), new BigDecimal(tokens[5]),
                Integer.parseInt(tokens[6]));
    }

    @Override
    public void run() {
        try {
            this.gettingOnline();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
