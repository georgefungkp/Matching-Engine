package equity.fix.client;

import quickfix.*;
import quickfix.field.OrdType;
import quickfix.field.Side;

import java.math.BigDecimal;

public class FIXClient {
    public static void main(String[] args) throws ConfigError, FieldConvertError {
        SessionSettings settings = new SessionSettings("client_fix.cfg");
        FIXTradeClientApp application = new FIXTradeClientApp();
        MessageStoreFactory storeFactory = new FileStoreFactory(settings);
        LogFactory logFactory = new FileLogFactory(settings);
        MessageFactory messageFactory = new DefaultMessageFactory();

        Initiator initiator = new SocketInitiator(application, storeFactory, settings, logFactory, messageFactory);
        initiator.start();
        SessionID sessionID = initiator.getSessions().get(0);
        application.sendNewOrderSingle(sessionID, "Futu", "1", Side.BUY, OrdType.LIMIT, 100, BigDecimal.valueOf(100.01));
        application.sendNewOrderSingle(sessionID, "Futu", "1", Side.SELL, OrdType.LIMIT, 50, BigDecimal.valueOf(99.99));
        application.onLogout(sessionID);
        // Create a new order single message
//        NewOrderSingle newOrder = new NewOrderSingle(
//            new ClOrdID("12345"),
//            new HandlInst(HandlInst.AUTOMATED_EXECUTION_ORDER_PRIVATE),
//            new Symbol("AAPL"),
//            new Side(Side.BUY),
//            new TransactTime(),
//            new OrdType(OrdType.MARKET)
//        );
//        newOrder.set(new OrderQty(100));
//
//        // Send the order
//        Session.sendToTarget(newOrder, "CLIENT1", "BROKER1");
    }
}
