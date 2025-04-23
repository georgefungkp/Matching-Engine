package equity.requesthandling;

import equity.externalparties.MarketDataJob;
import equity.externalparties.ResultingTradeJob;
import equity.fix.server.FIXTradeServerApp;
import equity.orderprocessing.LimitOrderMatchingJob;
import equity.orderprocessing.OrderProcessingJob;
import equity.vo.MarketData;
import equity.vo.Order;
import equity.vo.OrderBook;
import equity.vo.Trade;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import static util.ReadConfig.dotenv;


public class MatchingEngine extends Thread {
    private static final int noOfAvailableThreads = Runtime.getRuntime().availableProcessors();
    private static final Logger log = LogManager.getLogger(MatchingEngine.class);
    private static final String SUCCESS_MSG_TEMPLATE = " is processing your order. Good Luck!";

    private static final LinkedBlockingQueue<Order> orderQueue = new LinkedBlockingQueue<>();
    private static final LinkedBlockingQueue<MarketData> marketDataQueue = new LinkedBlockingQueue<>();
    private static final LinkedBlockingQueue<Trade> resultingTradeQueue = new LinkedBlockingQueue<>();
    private final FIXTradeServerApp fixTradeServerApp;
    private final int noOfStock;
    private static final HashMap<String, OrderBook> orderBooks = new HashMap<>();
    private static final ConcurrentHashMap<String, Order> orderObjMapper = new ConcurrentHashMap<>();


    private boolean listening = true;


    public MatchingEngine() {
        System.out.println(System.getProperty("java.class.path"));
        fixTradeServerApp = new FIXTradeServerApp(orderQueue);
        new Thread(fixTradeServerApp).start();
        log.debug("Number of available threads in this machine: {}", noOfAvailableThreads);
        noOfStock = Integer.parseInt(Objects.requireNonNull(dotenv.get("no_of_stock")));
    }

    public static void main(String[] args) {
        MatchingEngine server = new MatchingEngine();
        server.startProcessingJobs();
        server.start();
    }

    /**
     * This method initializes and starts multiple order matching jobs for the stocks based on the configured number of stocks.
     * Each stock is associated with an OrderBook that contains bid and ask order maps. A new thread is created for each OrderBook
     * and a LimitOrderMatchingJob is executed on that thread to match bid and ask orders.
     */
    private void startOrderMatchingJobs(){
        for (int i=0;i<noOfStock;i++) {
            OrderBook orderBook = new OrderBook(String.format("%05d", i), "Stock " + i);
            orderBooks.put(String.format("%05d", i), orderBook);
            new Thread(new LimitOrderMatchingJob(orderBook, orderObjMapper, marketDataQueue, resultingTradeQueue)).start();
        }
    }


    public void startProcessingJobs() {
        startOrderMatchingJobs();
        new Thread(new OrderProcessingJob(orderQueue, orderBooks, orderObjMapper)).start();
        new Thread(new MarketDataJob(marketDataQueue)).start();
        new Thread(new ResultingTradeJob(resultingTradeQueue, this.fixTradeServerApp)).start();
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
        // Create a ServerSocket at certain port
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
                    log.debug("Order Queue: {}", orderQueue.size());
                    out.writeUTF(server.getLocalSocketAddress() + SUCCESS_MSG_TEMPLATE);
                } catch (Exception e) {
                    log.error(e);
                    listening = false;
                }
                server.close();
            }
        }
    }


    /**
     * Creates an Order object based on the provided input value.
     *
     * @param value the string value containing order information separated by ":" in the format:
     *              "stockNo : brokerId : orderType : buyOrSell : price : quantity"
     * @return the created Order object with the parsed order details
     */
    private Order createOrder(String value) {
//        		String message = order.getStockNo() + ":" + order.getBrokerId() + ":" + order.getClientOrdID() + ":"
//				+ order.getOrderType() + ":" + order.getBuyOrSell() + ":"
//				+ order.getPrice() + ":" + order.getQuantity();
        String[] tokens = value.split(":");
        return new Order(tokens[0], tokens[1], tokens[2], tokens[3], tokens[4], Double.valueOf(tokens[5]),
                Integer.parseInt(tokens[6]), ZonedDateTime.now(), ZonedDateTime.now());
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
