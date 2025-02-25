package equity.fix.client;

import quickfix.ConfigError;
import quickfix.FieldConvertError;
import quickfix.field.OrdType;
import quickfix.field.Side;

import java.math.BigDecimal;

public class FIXClient {
    public static void main(String[] args) throws ConfigError, FieldConvertError {
//        SessionSettings settings = new SessionSettings("client_fix.cfg");
//        FIXTradeClientApp appl = new FIXTradeClientApp();
//        MessageStoreFactory storeFactory = new FileStoreFactory(settings);
//        LogFactory logFactory = new FileLogFactory(settings);
//        MessageFactory messageFactory = new DefaultMessageFactory();
//
//        Initiator initiator = new SocketInitiator(appl, storeFactory, settings, logFactory, messageFactory);
//        initiator.start();
        FIXTradeClientApp appl = new FIXTradeClientApp();
        appl.onLogon(appl.getSessionID());

        appl.sendNewOrderSingle(appl.getSessionID(), "Futu", "1", Side.BUY, OrdType.LIMIT, 100, BigDecimal.valueOf(100.01));
        appl.sendNewOrderSingle(appl.getSessionID(), "Futu", "1", Side.SELL, OrdType.LIMIT, 50, BigDecimal.valueOf(99.99));
//        appl.onLogout(appl.getSessionID());

    }
}
