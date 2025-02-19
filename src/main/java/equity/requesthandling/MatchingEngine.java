package equity.requesthandling;

import equity.externalparties.MarketDataJob;
import equity.externalparties.ResultingTradeJob;
import equity.fix.server.FIXTradeServerApp;
import equity.orderprocessing.OrderProcessingJob;
import equity.vo.MarketData;
import equity.vo.OrderRequest;
import equity.vo.Trade;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import quickfix.ConfigError;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;

import static util.ReadConfig.dotenv;

public class MatchingEngine extends Thread {
    private static final int noOfAvailableThreads = Runtime.getRuntime().availableProcessors();
    private static final Logger log = LogManager.getLogger(MatchingEngine.class);
    private static final String SUCCESS_MSG_TEMPLATE = " is processing your order. Good Luck!";


    private static final LinkedBlockingQueue<OrderRequest> orderQueue = new LinkedBlockingQueue<>();

    private static final LinkedBlockingQueue<MarketData> marketDataQueue = new LinkedBlockingQueue<>();

    private static final LinkedBlockingQueue<Trade> resultingTradeQueue = new LinkedBlockingQueue<>();

    private boolean listening = true;

    public MatchingEngine() throws ConfigError, InterruptedException {
        System.out.println(System.getProperty("java.class.path"));
//        this.orderQueue = orderQueue;
//        this.marketDataQueue = marketDataQueue;
//        this.resultingTradeQueue = resultingTradeQueue;
        new Thread(new FIXTradeServerApp(orderQueue)).start();
        log.debug("Number of available threads in this machine: {}", noOfAvailableThreads);
    }

    public static void main(String[] args) throws ConfigError, InterruptedException {
//        LinkedBlockingQueue<OrderRequest> orderQueue = new LinkedBlockingQueue<>();
//        LinkedBlockingQueue<MarketData> marketDataQueue = new LinkedBlockingQueue<>();
//        LinkedBlockingQueue<Trade> resultingTradeQueue = new LinkedBlockingQueue<>();

//        MatchingEngine server = new MatchingEngine(orderQueue, marketDataQueue, resultingTradeQueue);
        MatchingEngine server = new MatchingEngine();
        server.startProcessingJobs();
        server.start();
    }

    public void startProcessingJobs() {
        new Thread(new OrderProcessingJob(orderQueue, marketDataQueue, resultingTradeQueue)).start();
        new Thread(new MarketDataJob(marketDataQueue)).start();
        new Thread(new ResultingTradeJob(resultingTradeQueue)).start();
    }


    private void processOrderRequest() throws IOException {
        // Create a ServerSocket at certain port
        try (ServerSocket serverSocket = new ServerSocket(Integer.parseInt(Objects.requireNonNull(dotenv.get("port_number"))))) {
            while (listening) {
                // Await client connection
                log.debug("Waiting for client on port {}...", serverSocket.getLocalPort());
                Socket server = serverSocket.accept();
                log.debug("Just connected to {}", server.getRemoteSocketAddress());
                try (BufferedReader in = new BufferedReader(new InputStreamReader(server.getInputStream()));
                     DataOutputStream out = new DataOutputStream(server.getOutputStream())) {
                    OrderRequest order = createOrder(in.readLine());
                    orderQueue.put(order);
                    log.debug("Order Queue: {}", orderQueue);
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
     * Creates an OrderRequest object based on the provided input value.
     *
     * @param value the string value containing order information separated by ":" in the format:
     *              "stockNo : brokerId : orderType : buyOrSell : price : quantity"
     * @return the created OrderRequest object with the parsed order details
     */
    private OrderRequest createOrder(String value) {
        String[] tokens = value.split(":");
        return new OrderRequest(tokens[0], tokens[1], tokens[2], tokens[3], new BigDecimal(tokens[4]),
                Integer.parseInt(tokens[5]));
    }

    public void run() {
        try {
            this.processOrderRequest();
        } catch (IOException e) {
            log.error(e);
            e.printStackTrace();
        }
    }
}
