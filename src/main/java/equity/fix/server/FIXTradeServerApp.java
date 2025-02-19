package equity.fix.server;

import equity.vo.OrderRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import quickfix.*;
import quickfix.field.*;
import quickfix.fix44.ExecutionReport;
import quickfix.fix44.MessageCracker;
import quickfix.fix44.NewOrderSingle;

import java.math.BigDecimal;
import java.util.concurrent.LinkedBlockingQueue;

import static quickfix.field.OrdType.MARKET;
import static quickfix.field.Side.BUY;



public class FIXTradeServerApp extends MessageCracker implements Application, Runnable {
    private static final Logger log = LogManager.getLogger(FIXTradeServerApp.class);
    private final LinkedBlockingQueue<OrderRequest> orderQueue;

    public void run() {
        try {
            // Load FIX settings from a configuration file0
            SessionSettings settings = new SessionSettings("server_fix.cfg");
            MessageStoreFactory storeFactory = new FileStoreFactory(settings);
            LogFactory logFactory = new FileLogFactory(settings);
            MessageFactory messageFactory = new DefaultMessageFactory();
            // Start the FIX acceptor (server)
            SocketAcceptor acceptor = null;
            acceptor = new SocketAcceptor(this, storeFactory, settings, logFactory, messageFactory);
            acceptor.start();
            // Keep the server running
            log.debug("FIX server started. Press Ctrl+C to stop.");
            Thread.sleep(Long.MAX_VALUE);

            // Stop the FIX acceptor
            acceptor.stop();
        } catch (ConfigError | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public FIXTradeServerApp(LinkedBlockingQueue<OrderRequest> orderQueue) throws ConfigError, InterruptedException {
        this.orderQueue = orderQueue;
    }

    @Override
    public void onCreate(SessionID sessionID) {
        log.debug("Session created: " + sessionID);
    }

    @Override
    public void onLogon(SessionID sessionID) {
        log.debug("Logged on: " + sessionID);
    }

    @Override
    public void onLogout(SessionID sessionID) {
        log.debug("Logged out: " + sessionID);
    }

    @Override
    public void toAdmin(Message message, SessionID sessionID) {
        log.debug("toAdmin: " + message);
    }

    @Override
    public void fromAdmin(Message message, SessionID sessionID) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {
        log.debug("fromAdmin: " + message);
    }

    @Override
    public void toApp(Message message, SessionID sessionID) throws DoNotSend {
        log.debug("toApp: " + message);
    }

    @Override
    public void fromApp(Message message, SessionID sessionID) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        log.debug("fromApp: " + message);
        crack(message, sessionID); // Route the message to the appropriate handler
    }

    @Override
    public void onMessage(NewOrderSingle newOrder, SessionID sessionID) throws FieldNotFound {
        // Handle New Order Single (D) message
        log.debug("Received New Order Single (D)");

        // Extract fields from the order
//        String cliendOrderID = newOrder.getClOrdID().getValue();
        String brokerID = newOrder.getClOrdID().getValue();
        String stockNo = newOrder.getSymbol().getValue();
        String direction = (newOrder.getSide().getValue() == BUY)? "B": "S";
        String orderType = (newOrder.getOrdType().getValue() == MARKET)? "M": "L";
        double quantity = newOrder.getOrderQty().getValue();
        double price = newOrder.getPrice().getValue();


        log.debug("Stock Code: {}", stockNo);
        log.debug("Buy or Sell: {}", direction);
        log.debug("Quantity: {}", quantity);
        log.debug("Price: {}", price);

        try {
            orderQueue.put(new OrderRequest(stockNo, brokerID, orderType, direction,
                    BigDecimal.valueOf(price), (int)quantity));
            log.debug("Put the {} order of {} to the order queue", direction, stockNo);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // Send an Execution Report (8) to acknowledge the order
//        sendExecutionReport(sessionID, clOrdID, symbol, side, orderQty, price);
    }

    public void sendExecutionReport(SessionID sessionID, String clOrdID, String symbol, char side, double orderQty, double price) {
        // Create an Execution Report (8) message
        ExecutionReport executionReport = new ExecutionReport(
            new OrderID("BRK12345"), // Broker-assigned order ID
            new ExecID("EXEC9876"),  // Execution ID
            new ExecType(ExecType.FILL), // Execution type
            new OrdStatus(OrdStatus.FILLED), // Order status
            new Side(side),
            new LeavesQty(0), // No remaining quantity
            new CumQty(orderQty), // Cumulative quantity filled
            new AvgPx(price) // Average price
        );
        executionReport.set(new ClOrdID(clOrdID));
        executionReport.set(new Symbol(symbol));
        executionReport.set(new LastQty(orderQty));
        executionReport.set(new LastPx(price));

        // Send the Execution Report
        try {
            Session.sendToTarget(executionReport, sessionID);
            log.debug("Sent Execution Report (8)");
        } catch (SessionNotFound e) {
            log.error("Session not found: " + e.getMessage());
        }
    }

    public static void main(String[] args) throws ConfigError, InterruptedException {

    }
}