package equity.externalparties;

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
	private LinkedBlockingQueue<MarketData> marketDataQueue;

	public MarketDataJob(LinkedBlockingQueue<MarketData> marketDataQueue) {
		this.marketDataQueue = marketDataQueue;
	}

	@Override
	public void run() {
		while (true) {
			try {
				MarketData data = marketDataQueue.take();

				Path path = Paths.get(dotenv.get("marketData") + data.getStockNo() + "_"
						+ LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".txt");
				Files.deleteIfExists(path);
				FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
				fileChannel.lock();
				String bestBidTxt = (data.getBestBid() == null) ? "" : data.getBestBid().toString();
				String bestAskTxt = (data.getBestAsk() == null) ? "" : data.getBestAsk().toString();
				String nominalPriceTxt = (data.getNominalPrice() == null)? "": data.getNominalPrice().toString();

				String message = "Stock Name:" + data.getStockNo() + "\n" + "Best Bid Price:" + bestBidTxt + "\n"
						+ "Best Ask Price:" + bestAskTxt + "\n" + "Nominal Price:" + nominalPriceTxt + "\n";
				message += "Bid orders\n";
				for (Entry<BigDecimal, PriorityQueue<Order>> entry : data.getBidMap().entrySet()) {
					String priceTxt = ""; 
					if (entry.getKey().equals(BigDecimal.valueOf(Integer.MAX_VALUE))){
						priceTxt = "M";
					}
					message += priceTxt + "\n";
					for (Order order : entry.getValue()) {
						message += order.getBrokerId() + " " + order.getQuantity() + "\n";
					}
				}
				message += "Ask orders\n";
				for (Entry<BigDecimal, PriorityQueue<Order>> entry : data.getAskMap().entrySet()) {
					String priceTxt = ""; 
					if (entry.getKey().equals(BigDecimal.valueOf(0))){
						priceTxt = "M";
					}
					message += priceTxt + "\n";
					for (Order order : entry.getValue()) {
						message += order.getBrokerId() + " " + order.getQuantity() + "\n";
					}
				}
//				System.out.println("Saving " + message + " into marketing_data." + data.getStockNo() + "_"
//						+ LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".txt");
				fileChannel.write(ByteBuffer.wrap(message.getBytes()));
				fileChannel.close(); // also releases the lock
			} catch (InterruptedException | IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

}
