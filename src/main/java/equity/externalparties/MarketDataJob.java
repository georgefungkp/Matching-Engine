package equity.externalparties;

import equity.objectpooling.MarketData;
import equity.objectpooling.Order;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.LinkedBlockingQueue;

import static util.ReadConfig.dotenv;

public class MarketDataJob implements Runnable {
	private static final Logger log = LogManager.getLogger(MarketDataJob.class);
	private final LinkedBlockingQueue<MarketData> marketDataQueue;
	private boolean listening = true;

	public MarketDataJob(LinkedBlockingQueue<MarketData> marketDataQueue) {
		this.marketDataQueue = marketDataQueue;
	}

	/**
	 * Continuously listens for market data updates from the marketDataQueue and writes the data to a file in a specific format.
	 * The file will contain information such as stock name, best bID and ask prices, last trade price, and lists of bID and ask orders.
	 * The file is named based on the stock number and the current date appended with a '.txt' extension.
	 * If an exception occurs while processing the data or writing to the file, the method will log the error and stop listening for updates.
	 */
	@Override
	public void run() {
		while (listening) {
			try {
				MarketData data = marketDataQueue.take();

				Path path = Paths.get(dotenv.get("marketData") + data.stockNo() + "_"
						+ LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".txt");
				Files.deleteIfExists(path);
				FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
				fileChannel.lock();
				String bestBidTxt = (data.bestBid() == null) ? "" : data.bestBid().toString();
				String bestAskTxt = (data.bestAsk() == null) ? "" : data.bestAsk().toString();
				String lastTradePrice = (data.lastTradePrice() == null)? "": data.lastTradePrice().toString();

                String message = "Stock Name:" + data.stockNo() + "\n" + "Best Bid Price:" + bestBidTxt + "\n"
                        + "Best Ask Price:" + bestAskTxt + "\n" + "Last Trade Price:" + lastTradePrice + "\n" + "Bid orders\n" +
                        orderBookToTxt(data.bidMap()) +
                        "Ask orders\n" +
                        orderBookToTxt(data.askMap());
				fileChannel.write(ByteBuffer.wrap(message.getBytes()));
				fileChannel.close(); // also releases the lock
			} catch (InterruptedException | IOException e) {
				log.error(e);
				listening = false;
			}
		}
	}

	public StringBuilder orderBookToTxt(TreeMap<Double, LinkedList<Order>> orderBook){
		StringBuilder message = new StringBuilder();
		for (Entry<Double, LinkedList<Order>> entry : orderBook.entrySet()) {
			for (Order order : entry.getValue()) {
				message.append(order.getBrokerID()).append("-").append(order.getClientOrdID()).append(" ").append(order.getPrice()).append(" ").append(order.getQuantity()).append("\n");
			}
		}
		return message;
	}

}
