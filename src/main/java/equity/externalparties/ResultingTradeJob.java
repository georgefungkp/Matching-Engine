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

	/**
	 * This method continuously listens for trade data from a queue and processes it by writing the information to a CSV file
	 * and sending a FIX message if applicable. It runs in a loop until the listening flag is set to false.
	 */
	@Override
	public void run() {
		while (listening) {
			try {
				Trade tradeData = resultingTradeQueue.take();

				Path path = Paths.get(dotenv.get("tradeData") + tradeData.stockNo() + "_"
						+ LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".csv");
				FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
				fileChannel.lock();
				// Stock No, Bid Broker Id, Bid Order id, Sell Broker Id, Sell order id, Executed Price, Qty, Executed Time
				String message = String.format("%s,%s,%s,%s,%s,%s %s %s\r\n", tradeData.stockNo(), tradeData.buyBrokerID(), tradeData.buyOrderID(),
						tradeData.sellBrokerID(), tradeData.sellOrderID(), tradeData.executedPrice(), tradeData.executedQty(),
						tradeData.executionDateTime());
				fileChannel.write(ByteBuffer.wrap(message.getBytes()));
				fileChannel.close(); // also releases the lock

				// Send FIX message if applicable
				if (fixTradeServer.getSessionID() != null){
 					fixTradeServer.sendExecutionReport(fixTradeServer.getSessionID(),
							tradeData.buyBrokerID(), tradeData.stockNo(), new Side(Side.BUY),
							new ExecType(ExecType.TRADE), new OrdStatus(OrdStatus.FILLED),
							tradeData.executedQty(), tradeData.executedPrice());
 					fixTradeServer.sendExecutionReport(fixTradeServer.getSessionID(),
							tradeData.sellBrokerID(), tradeData.stockNo(), new Side(Side.SELL),
							new ExecType(ExecType.TRADE), new OrdStatus(OrdStatus.FILLED),
							tradeData.executedQty(), tradeData.executedPrice());
				}

			} catch (InterruptedException | IOException e) {
				log.error(e);
				listening = false;
			}
		}
	}
}
