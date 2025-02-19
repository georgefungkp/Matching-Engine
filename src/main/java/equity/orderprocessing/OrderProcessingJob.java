package equity.orderprocessing;

import equity.vo.MarketData;
import equity.vo.OrderBooksForStock;
import equity.vo.OrderRequest;
import equity.vo.Trade;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;

public class OrderProcessingJob implements Runnable {
	private static final Logger log = LogManager.getLogger(OrderProcessingJob.class);
	private static final int noOfStock = 10;
	// First element is stock 1, 2nd element is stock 2, ....
	List<OrderBooksForStock> listOfStocks = new ArrayList<>(noOfStock);
	private final LinkedBlockingQueue<OrderRequest> orderQueue;
	private LinkedBlockingQueue<MarketData> marketDataQueue;
	private LinkedBlockingQueue<Trade> resultingTradeQueue;

	public OrderProcessingJob(LinkedBlockingQueue<OrderRequest> orderQueue,
			LinkedBlockingQueue<MarketData> marketDataQueue, LinkedBlockingQueue<Trade> resultingTradeQueue) {
		this.orderQueue = orderQueue;
		this.marketDataQueue = marketDataQueue;
		this.resultingTradeQueue = resultingTradeQueue;
	}

	private void createOrderBook() {
		for (int i = 1; i <= noOfStock; i++) {
			listOfStocks.add(
					new OrderBooksForStock(String.format("%05d", i), new BigDecimal(0), "Stock " + i));
		}
	}

	@Override
	public void run() {
		createOrderBook();
		while (true) {
			log.debug("Getting order from Queue");
			try {
				OrderRequest orderReq = orderQueue.take();
				if (listOfStocks.size() < Integer.parseInt(orderReq.stockNo())) {
					log.debug("Stock no is incorrect. Order is ignored");
				} else {
					OrderBooksForStock stock = listOfStocks.get(Integer.parseInt(orderReq.stockNo()) - 1);
					List<Trade> tradeList = stock.addBid(orderReq);

					Optional<BigDecimal> nominalAmt = Optional.empty();
					for (Trade trade : tradeList) {
						if (nominalAmt.isEmpty() || nominalAmt.get().compareTo(trade.getPrice()) < 0) {
							nominalAmt = Optional.of(trade.getPrice());
						}
					}
                    nominalAmt.ifPresent(stock::setNominalPrice);
					marketDataQueue.put(new MarketData(stock.getStockNo(), stock.getBestBid(), stock.getBestAsk(),
							stock.getNominalPrice(), Timestamp.from(Instant.now()), stock.getBidMap(), stock.getAskMap()));
					if (!tradeList.isEmpty()) {
						for (Trade trade : tradeList) {
							resultingTradeQueue.put(trade);
						}
					}
				}
			} catch (InterruptedException e) {
				log.error(e);
			}
		}

	}

}
