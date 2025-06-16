package equity.fix.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import quickfix.*;
import quickfix.Message;
import quickfix.MessageFactory;
import quickfix.field.*;
import quickfix.fix44.*;
import quickfix.fix44.MessageCracker;

import java.math.BigDecimal;

public class FIXTradeClientApp extends MessageCracker implements Application {
    private static final Logger log = LogManager.getLogger(FIXTradeClientApp.class);
    private SessionID sessionID;

    public SessionID getSessionID() {
        return sessionID;
    }


    public FIXTradeClientApp() throws ConfigError {
        SessionSettings settings = new SessionSettings("client_fix.cfg");
        MessageStoreFactory storeFactory = new FileStoreFactory(settings);
        LogFactory logFactory = new FileLogFactory(settings);
        MessageFactory messageFactory = new DefaultMessageFactory();

        Initiator initiator = new SocketInitiator(this, storeFactory, settings, logFactory, messageFactory);
        initiator.start();
        this.sessionID = initiator.getSessions().getFirst();
        log.debug("Session created: {}", sessionID);
    }

    @Override
    public void onCreate(SessionID sessionID) {

    }

    @Override
    public void onLogon(SessionID sessionID) {
        // Create and send Logon message
        Logon logon = new Logon();
        logon.set(new quickfix.field.HeartBtInt(30)); // example heartbeat interval
        logon.set(new quickfix.field.EncryptMethod(0)); // 0 - none
        logon.set(new quickfix.field.ResetSeqNumFlag(true));

        log.debug("Logged on: {}", sessionID);
    }

    @Override
    public void onLogout(SessionID sessionID) {
        // Create and send Logout message
        Logout logout = new Logout();
        Session.lookupSession(this.sessionID).send(logout);
    }

    @Override
    public void toAdmin(Message message, SessionID sessionID) {
        log.debug("toAdmin: {}", message);
    }

    @Override
    public void fromAdmin(Message message, SessionID sessionID) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {
        log.debug("fromAdmin: {}", message);
    }

    @Override
    public void toApp(Message message, SessionID sessionID) throws DoNotSend {
        log.debug("toApp: {}", message);
    }

    @Override
    public void fromApp(Message message, SessionID sessionID) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        log.debug("fromApp: {}", message);
        crack(message, sessionID); // Route the message to the appropriate handler
    }

    public void sendNewOrderSingle(SessionID sessionID, String brokerID, String stockNo, char Side, char orderType,
                                   int qty, BigDecimal price) {
        // Create a New Order Single (D) message
        NewOrderSingle newOrder = new NewOrderSingle(
            new ClOrdID(brokerID), //Broker ID
            new Side(Side),
            new TransactTime(),
            new OrdType(orderType)
        );
        newOrder.set(new Symbol(stockNo));
        newOrder.set(new OrderQty(qty));
        newOrder.set(new Price(price.doubleValue()));
        newOrder.set(new HandlInst(HandlInst.AUTOMATED_EXECUTION_ORDER_PRIVATE_NO_BROKER_INTERVENTION));

        // Send the order
        try {
            Session.sendToTarget(newOrder, sessionID);
            log.debug("Sent New Order Single (D)");
        } catch (SessionNotFound e) {
            log.error("Session not found: {}", e.getMessage());
        }
    }

    @Override
    public void onMessage(ExecutionReport executionReport, SessionID sessionID) throws FieldNotFound {
        // Handle Execution Report (8) message
        char execType = executionReport.getExecType().getValue();
        char ordStatus = executionReport.getOrdStatus().getValue();

        if (execType == ExecType.PENDING_NEW && ordStatus == OrdStatus.PENDING_NEW) {
            log.debug("Received Execution Report: Order Pending New");
        } else if (execType == ExecType.NEW && ordStatus == OrdStatus.NEW) {
            log.debug("Received Execution Report: Order Accepted");
        } else if (execType == ExecType.TRADE && ordStatus == OrdStatus.FILLED) {
            log.debug("Received Execution Report: Order Filled");
            log.debug("Stock Code: {}, Buy(1)/Sell(2): {}, LastQty: {}, LastPx: {}",
                    executionReport.getSymbol().getValue(),
                    executionReport.getSide().toString(),
                    executionReport.getCumQty().getValue(),
                    executionReport.getAvgPx().getValue()
            );
        }
    }


}