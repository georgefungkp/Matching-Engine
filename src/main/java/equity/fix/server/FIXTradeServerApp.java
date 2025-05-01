package equity.fix.server;

import equity.objectpooling.OrderManager;
import equity.objectpooling.Order;
import equity.objectpooling.Order.Action;
import equity.objectpooling.Order.OrderType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import quickfix.*;
import quickfix.field.*;
import quickfix.fix44.ExecutionReport;
import quickfix.fix44.MessageCracker;
import quickfix.fix44.NewOrderSingle;
import util.SequenceGenerator;

import java.util.concurrent.LinkedBlockingQueue;

import static quickfix.field.OrdType.MARKET;
import static quickfix.field.Side.BUY;


/**
 * FIXTradeServerApp represents a FIX server application that implements the Application and Runnable interfaces.
 * It handles incoming FIX messages and processes new order requests.
 */
public class FIXTradeServerApp extends MessageCracker implements Application, Runnable {
    private static final Logger log = LogManager.getLogger(FIXTradeServerApp.class);
    private final LinkedBlockingQueue<Order> orderQueue;
    private final SequenceGenerator executionIDGenerator = new SequenceGenerator(); //thread-safe
    private SessionID sessionID;

    public void run() {
        try {
            // Load FIX settings from a configuration file
            final SocketAcceptor acceptor = getSocketAcceptor();
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

    private @NotNull SocketAcceptor getSocketAcceptor() throws ConfigError {
        SessionSettings settings = new SessionSettings("server_fix.cfg");
        MessageStoreFactory storeFactory = new FileStoreFactory(settings);
        LogFactory logFactory = new FileLogFactory(settings);
        MessageFactory messageFactory = new DefaultMessageFactory();
        // Start the FIX acceptor (server)
        SocketAcceptor acceptor;
        acceptor = new SocketAcceptor(this, storeFactory, settings, logFactory, messageFactory);
        return acceptor;
    }


    @Override
    public void onMessage(NewOrderSingle newOrder, SessionID sessionID) throws FieldNotFound {
        // Handle New Order Single (D) message
        log.debug("Received New Order Single (D)");

        // Extract fields from the order
        String clientOrdID = newOrder.getClOrdID().getValue();
        String brokerID = newOrder.getClOrdID().getValue();
        String stockNo = newOrder.getSymbol().getValue();
        Action action = (newOrder.getSide().getValue() == BUY)? Action.BUY : Action.SELL;
        OrderType orderType = (newOrder.getOrdType().getValue() == MARKET)? OrderType.MARKET: OrderType.LIMIT;
        double quantity = newOrder.getOrderQty().getValue();
        double price = newOrder.getPrice().getValue();

        log.debug("Stock Code: {}", stockNo);
        log.debug("Buy or Sell: {}", action);
        log.debug("Quantity: {}", quantity);
        log.debug("Price: {}", price);

        try {
            // Send an Execution Report (8) to acknowledge the order
            sendExecutionReport(sessionID, clientOrdID, newOrder, new ExecType(ExecType.NEW), new OrdStatus(OrdStatus.NEW));
            orderQueue.put(OrderManager.requestOrderObj(stockNo, brokerID, clientOrdID, orderType, action,
                    price, (int)quantity));
            log.debug("Put the {} order of {} to the order queue", action, stockNo);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * Sends an execution report based on the provIDed parameters.
     *
     * @param sessionID the session ID to which the report will be sent
     * @param clientOrdID the client order ID
     * @param stockNo the stock number
     * @param side the Side (buy or sell) of the order
     * @param execType the execution type
     * @param ordStatus the order status
     * @param filledQty the quantity filled
     * @param execPrice the execution price
     */
    public void sendExecutionReport(SessionID sessionID, String clientOrdID, String stockNo, Side side,
                                    ExecType execType, OrdStatus ordStatus, int filledQty, Double execPrice){
        ExecutionReport executionReport = new ExecutionReport(
            new OrderID(clientOrdID), // Broker-assigned order ID
            new ExecID(String.valueOf(executionIDGenerator.getNextSequence())),  // Execution ID
            execType, // Execution type
            ordStatus, // Order status
            side,
            new LeavesQty(0), // No remaining quantity
            new CumQty(filledQty), // Cumulative quantity filled
            new AvgPx(execPrice) // Average price
        );
        executionReport.set(new Symbol(stockNo)); // replace with your symbol
        try {
            Session.sendToTarget(executionReport, sessionID);
            log.debug("Sent Execution Report");
        } catch (SessionNotFound e) {
            log.error("Session not found: {}", e.getMessage());
        }
    }

    public void sendExecutionReport(SessionID sessionID, String clientOrdID, NewOrderSingle newOrder,
                                    ExecType execType, OrdStatus ordStatus) throws FieldNotFound {
        // Create an Execution Report (8) message
        ExecutionReport executionReport = new ExecutionReport(
            new OrderID(clientOrdID), // Broker-assigned order ID
            new ExecID(String.valueOf(executionIDGenerator.getNextSequence())),  // Execution ID
            execType, // Execution type
            ordStatus, // Order status
            new Side(newOrder.getSide().getValue()),
            new LeavesQty(newOrder.getOrderQty().getValue()), // No remaining quantity
            new CumQty(0), // Cumulative quantity filled
            new AvgPx(newOrder.getPrice().getValue()) // Average price
        );
        executionReport.set(new ClOrdID(clientOrdID));
        executionReport.set(new Symbol(newOrder.getSymbol().toString()));

        // Send the Execution Report
        try {
            Session.sendToTarget(executionReport, sessionID);
            log.debug("Sent Execution Report");
        } catch (SessionNotFound e) {
            log.error("Session not found: {}", e.getMessage());
        }
    }

    public FIXTradeServerApp(LinkedBlockingQueue<Order> orderQueue) {
        this.orderQueue = orderQueue;
    }

    @Override
    public void onCreate(SessionID sessionID) {
        log.debug("Session created: {}", sessionID);
    }

    @Override
    public void onLogon(SessionID sessionID) {
        this.sessionID = sessionID;
        log.debug("Logged on: {}", sessionID);
    }

    @Override
    public void onLogout(SessionID sessionID) {
        log.debug("Logged out: {}", sessionID);
    }

    @Override
    public void toAdmin(Message message, SessionID sessionID) {
        log.debug("toAdmin: {}", message);
    }

    @Override
    public void fromAdmin(Message message, SessionID sessionID) {
        log.debug("fromAdmin: {}", message);
    }

    @Override
    public void toApp(Message message, SessionID sessionID) {
        log.debug("toApp: {}", message);
    }

    @Override
    public void fromApp(Message message, SessionID sessionID) throws FieldNotFound, IncorrectTagValue, UnsupportedMessageType {
        log.debug("fromApp: {}", message);
        crack(message, sessionID); // Route the message to the appropriate handler
    }

    public SessionID getSessionID() {
        return sessionID;
    }


}