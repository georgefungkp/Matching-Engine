package equity.externalparties;

import equity.vo.Trade;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
	private boolean listening = true;

	public ResultingTradeJob(LinkedBlockingQueue<Trade> resultingTradeQueue) {
		this.resultingTradeQueue = resultingTradeQueue;
	}

	@Override
	public void run() {
		while (listening) {
			try {
				Trade data = resultingTradeQueue.take();

				Path path = Paths.get(dotenv.get("tradeData") + data.getStockNo() + "_"
						+ LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".csv");
				FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
				fileChannel.lock();
				// Stock No, Buy Broker Id, Sell Broker Id, Executed Price, Qty, Executed Time
				String message = String.format("%s,%s,%s,%s,%s,%s\r\n", data.getStockNo(), data.getBuyBrokerID(),
						data.getSellBrokerID(), data.getExecutedPrice(), data.getExecutedQty(),
						data.getExecutionDateTime());
//				log.debug("Appending " + message + " into trade_data_" + data.getStockNo() + "_"
//						+ LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".txt");
				fileChannel.write(ByteBuffer.wrap(message.getBytes()));
				fileChannel.close(); // also releases the lock

			} catch (InterruptedException | IOException e) {
				// TODO Auto-generated catch block
				log.error(e);
				listening = false;
			}
		}
	}
}
