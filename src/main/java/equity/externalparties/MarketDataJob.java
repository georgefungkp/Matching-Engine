package equity.externalparties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map.Entry;
import java.util.PriorityQueue;
import java.util.concurrent.LinkedBlockingQueue;

import equity.vo.MarketData;
import equity.vo.Order;
import static util.ReadConfig.dotenv;

public class MarketDataJob implements Runnable {
	private static final Logger log = LogManager.getLogger(MarketDataJob.class);
	private final LinkedBlockingQueue<MarketData> marketDataQueue;

	public MarketDataJob(LinkedBlockingQueue<MarketData> marketDataQueue) {
		this.marketDataQueue = marketDataQueue;
	}

	@Override
	public void run() {
		while (true) {
			try {
				MarketData data = marketDataQueue.take();

				Path path = Paths.get(dotenv.get("marketData") + data.stockNo() + "_"
						+ LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".txt");
				Files.deleteIfExists(path);
				FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
				fileChannel.lock();
				String bestBidTxt = (data.bestBid() == null) ? "" : data.bestBid().toString();
				String bestAskTxt = (data.bestAsk() == null) ? "" : data.bestAsk().toString();
				String nominalPriceTxt = (data.nominalPrice() == null)? "": data.nominalPrice().toString();

				StringBuilder message = new StringBuilder("Stock Name:" + data.stockNo() + "\n" + "Best Bid Price:" + bestBidTxt + "\n"
                        + "Best Ask Price:" + bestAskTxt + "\n" + "Nominal Price:" + nominalPriceTxt + "\n");
				message.append("Bid orders\n");
				for (Entry<BigDecimal, PriorityQueue<Order>> entry : data.bidMap().entrySet()) {
					String priceTxt = ""; 
					if (entry.getKey().equals(BigDecimal.valueOf(Integer.MAX_VALUE))){
						priceTxt = "M";
					}
					message.append(priceTxt).append("\n");
					for (Order order : entry.getValue()) {
						message.append(order.getBrokerId()).append(" ").append(order.getQuantity()).append("\n");
					}
				}
				message.append("Ask orders\n");
				for (Entry<BigDecimal, PriorityQueue<Order>> entry : data.askMap().entrySet()) {
					String priceTxt = ""; 
					if (entry.getKey().equals(BigDecimal.valueOf(0))){
						priceTxt = "M";
					}
					message.append(priceTxt).append("\n");
					for (Order order : entry.getValue()) {
						message.append(order.getBrokerId()).append(" ").append(order.getQuantity()).append("\n");
					}
				}
//				log.debug("Saving " + message + " into marketing_data." + data.getStockNo() + "_"
//						+ LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".txt");
				fileChannel.write(ByteBuffer.wrap(message.toString().getBytes()));
				fileChannel.close(); // also releases the lock
			} catch (InterruptedException | IOException e) {
				// TODO Auto-generated catch block
				log.error(e);
			}
		}
	}

}
