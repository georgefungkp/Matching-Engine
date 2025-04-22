package equity.externalparties;

import equity.fix.server.FIXTradeServerApp;
import equity.vo.Trade;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import quickfix.field.ExecType;
import quickfix.field.OrdStatus;
import quickfix.field.Side;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.LinkedBlockingQueue;

import static util.ReadConfig.dotenv;

public class ResultingTradeJob implements Runnable {
	private static final Logger log = LogManager.getLogger(ResultingTradeJob.class);
	private final LinkedBlockingQueue<Trade> resultingTradeQueue;
	private final FIXTradeServerApp fixTradeServer;
	private boolean listening = true;

	public ResultingTradeJob(LinkedBlockingQueue<Trade> resultingTradeQueue, FIXTradeServerApp fixTradeServer) {
		this.resultingTradeQueue = resultingTradeQueue;
		this.fixTradeServer = fixTradeServer;
	}

	@Override
	public void run() {
		while (listening) {
			try {
				Trade data = resultingTradeQueue.take();

				Path path = Paths.get(dotenv.get("tradeData") + data.stockNo() + "_"
						+ LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".csv");
				FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
				fileChannel.lock();
				// Stock No, Buy Broker Id, Sell Broker Id, Executed Price, Qty, Executed Time
				String message = String.format("%s,%s,%s,%s,%s,%s\r\n", data.stockNo(), data.buyBrokerID(),
						data.sellBrokerID(), data.executedPrice(), data.executedQty(),
						data.executionDateTime());
//				log.debug("Appending " + message + " into trade_data_" + data.getStockNo() + "_"
//						+ LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".txt");
				fileChannel.write(ByteBuffer.wrap(message.getBytes()));
				fileChannel.close(); // also releases the lock

				// Send FIX message if applicable
				if (fixTradeServer.getSessionID() != null){
 					fixTradeServer.sendExecutionReport(fixTradeServer.getSessionID(),
							data.buyBrokerID(), data.stockNo(), new Side(Side.BUY),
							new ExecType(ExecType.TRADE), new OrdStatus(OrdStatus.FILLED),
							data.executedQty(), data.executedPrice());
 					fixTradeServer.sendExecutionReport(fixTradeServer.getSessionID(),
							data.sellBrokerID(), data.stockNo(), new Side(Side.SELL),
							new ExecType(ExecType.TRADE), new OrdStatus(OrdStatus.FILLED),
							data.executedQty(), data.executedPrice());
				}

			} catch (InterruptedException | IOException e) {
				// TODO Auto-generated catch block
				log.error(e);
				listening = false;
			}
		}
	}
}
