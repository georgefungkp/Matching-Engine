package equity.requesthandling;

import equity.externalparties.MarketDataJob;
import equity.externalparties.ResultingTradeJob;
import equity.orderprocessing.OrderProcessingJob;
import equity.vo.MarketData;
import equity.vo.OrderRequest;
import equity.vo.Trade;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;

import static util.ReadConfig.dotenv;

public class TradeServer extends Thread {
    private static final int noOfAvailableThreads = Runtime.getRuntime().availableProcessors();
    private static final Logger log = LogManager.getLogger(TradeServer.class);
    private static final String SUCCESS_MSG_TEMPLATE = " is processing your order. Good Luck!";
    private static final String ERROR_MSG_TEMPLATE = " has error. Order is rejected.";
    private final LinkedBlockingQueue<OrderRequest> orderQueue;
    private final LinkedBlockingQueue<MarketData> marketDataQueue;
    private final LinkedBlockingQueue<Trade> resultingTradeQueue;

    public TradeServer(LinkedBlockingQueue<OrderRequest> orderQueue, LinkedBlockingQueue<MarketData> marketDataQueue,
                       LinkedBlockingQueue<Trade> resultingTradeQueue) {
System.out.println(System.getProperty("java.class.path"));
        this.orderQueue = orderQueue;
        this.marketDataQueue = marketDataQueue;
        this.resultingTradeQueue = resultingTradeQueue;
        log.debug("Number of available threads in this machine: {}", noOfAvailableThreads);
    }

    public static void main(String[] args) {
        LinkedBlockingQueue<OrderRequest> orderQueue = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<MarketData> marketDataQueue = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<Trade> resultingTradeQueue = new LinkedBlockingQueue<>();

        TradeServer server = new TradeServer(orderQueue, marketDataQueue, resultingTradeQueue);
        server.startProcessingJobs();
        server.start();
    }

    public void startProcessingJobs() {
        new Thread(new OrderProcessingJob(orderQueue, marketDataQueue, resultingTradeQueue)).start();
        new Thread(new MarketDataJob(marketDataQueue)).start();
        new Thread(new ResultingTradeJob(resultingTradeQueue)).start();
    }

    public void processOrderRequest() {
        while (true) {
            try {
                processSingleOrderRequest();
            } catch (IOException e) {
                log.error(e);
                e.printStackTrace();
            }
        }
    }

    private void processSingleOrderRequest() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(Integer.parseInt(Objects.requireNonNull(dotenv.get("port_number"))))){
            Socket server = serverSocket.accept();
            try (DataInputStream in = new DataInputStream(server.getInputStream());
                DataOutputStream out = new DataOutputStream(server.getOutputStream())){
                log.debug("Waiting for client on port {}...", serverSocket.getLocalPort());
                log.debug("Just connected to {}", server.getRemoteSocketAddress());
                handleOrder(out, server, in.readUTF());
            }
        }
    }

    private void handleOrder(DataOutputStream out, Socket server, String receivedValue) throws IOException {
        try {
            OrderRequest order = createOrder(receivedValue);
            orderQueue.put(order);
            log.debug("Order Queue: {}", orderQueue);
            out.writeUTF(server.getLocalSocketAddress() + SUCCESS_MSG_TEMPLATE);
        } catch (Exception e) {
            log.error(e);
            out.writeUTF(server.getLocalSocketAddress() + ERROR_MSG_TEMPLATE);
        }
    }

    private OrderRequest createOrder(String value) {
        String[] tokens = value.split(":");
        return new OrderRequest(tokens[0], tokens[1], tokens[2], tokens[3], new BigDecimal(tokens[4]),
                Integer.parseInt(tokens[5]));
    }

    public void run() {
        this.processOrderRequest();
    }
}
