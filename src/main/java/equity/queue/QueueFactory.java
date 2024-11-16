package equity.queue;

import java.util.concurrent.LinkedBlockingQueue;

import equity.vo.MarketData;
import equity.vo.OrderRequest;
import equity.vo.Trade;

public class QueueFactory {

			
	public LinkedBlockingQueue<?> getQueue(QueueType type) {
		if (type == null) {
			return null;
		} else if (type == QueueType.PROCESSING_ORDER) {
			return new LinkedBlockingQueue<OrderRequest>();
		} else if (type == QueueType.MARKET_DATA) {
			return new LinkedBlockingQueue<MarketData>();
		} else if (type == QueueType.RESULTING_TRADE) {
			return new LinkedBlockingQueue<Trade>();
		}
		return null;
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
