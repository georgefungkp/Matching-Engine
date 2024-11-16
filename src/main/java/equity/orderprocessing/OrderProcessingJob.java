package equity.orderprocessing;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;

import equity.vo.MarketData;
import equity.vo.OrderRequest;
import equity.vo.OrderBooksForStock;
import equity.vo.Trade;

public class OrderProcessingJob implements Runnable {
	private static int noOfStock = 10;
	// First element is stock 1, 2nd element is stock 2, ....
	List<OrderBooksForStock> listOfStocks = new ArrayList<OrderBooksForStock>(noOfStock);
	private LinkedBlockingQueue<OrderRequest> orderQueue;
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
					new OrderBooksForStock(String.format("%05d", i), new BigDecimal(1), String.valueOf("Stock " + i)));
		}
	}

	@Override
	public void run() {
		createOrderBook();
		while (true) {
			System.out.println("Getting order from Queue");
			try {
				OrderRequest orderReq = orderQueue.take();
				if (listOfStocks.size() < Integer.parseInt(orderReq.getStockNo())) {
					System.out.println("Stock no is incorrect. Order is ignored");
				} else {
					OrderBooksForStock stock = listOfStocks.get(Integer.parseInt(orderReq.getStockNo()) - 1);
					List<Trade> tradeList = stock.addBid(orderReq);

					Optional<BigDecimal> nominalAmt = Optional.empty();
					for (Trade trade : tradeList) {
						if (!nominalAmt.isPresent() || nominalAmt.get().compareTo(trade.getPrice()) < 0) {
							nominalAmt = Optional.of(trade.getPrice());
						}
					}
					if (nominalAmt.isPresent()) {
						stock.setNominalPrice(nominalAmt.get());
					}
					marketDataQueue.put(new MarketData(stock.getStockNo(), stock.getBestBid(), stock.getBestAsk(),
							stock.getNominalPrice(), Timestamp.from(Instant.now()), stock.getBidMap(), stock.getAskMap()));
					if (tradeList.size() > 0) {
						for (Trade trade : tradeList) {
							resultingTradeQueue.put(trade);
						}
					}
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

	}

}
