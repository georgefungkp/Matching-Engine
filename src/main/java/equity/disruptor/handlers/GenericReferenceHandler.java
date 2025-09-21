// GenericReferenceHandler.java
package equity.disruptor.handlers;

import com.lmax.disruptor.EventHandler;
import equity.disruptor.events.GenericReference;
import equity.objectpooling.*;
import equity.orderprocessing.OrderProcessingJob;
import equity.externalparties.ResultingTradeJob;
import equity.externalparties.MarketDataJob;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GenericReferenceHandler<T> implements EventHandler<GenericReference<T>> {
    private static final Logger log = LogManager.getLogger(GenericReferenceHandler.class);
    
    private final OrderProcessingJob orderProcessingJob;
    private final ResultingTradeJob tradeJob;
    private final MarketDataJob marketDataJob;
    
    public GenericReferenceHandler(OrderProcessingJob orderProcessingJob, 
                                 ResultingTradeJob tradeJob,
                                 MarketDataJob marketDataJob) {
        this.orderProcessingJob = orderProcessingJob;
        this.tradeJob = tradeJob;
        this.marketDataJob = marketDataJob;
    }
    
    @Override
    public void onEvent(GenericReference<T> reference, long sequence, boolean endOfBatch) throws Exception {
        T object = reference.getRef();
        if (object == null) {
            return;
        }
        
        try {
            // Use instanceof to determine the type and process accordingly
            if (object instanceof Order order) {
                processOrder(order);
            } else if (object instanceof Trade trade) {
                processTrade(trade);
            } else if (object instanceof MarketData marketData) {
                processMarketData(marketData);
            } else {
                log.warn("Unknown object type: {}", object.getClass().getSimpleName());
            }
        } finally {
            // Always clear the reference
            reference.clear();
        }
    }
    
    private void processOrder(Order order) {
        if (orderProcessingJob != null) {
            orderProcessingJob.putOrder(order);
            // Note: Order pool management happens after matching, not here
        }
    }
    
    private void processTrade(Trade trade) {
        if (tradeJob != null) {
            tradeJob.processTrade(trade);
            // Return to pool after processing
            OrderPoolManager.returnTradeObj(trade);
        }
    }
    
    private void processMarketData(MarketData marketData) {
        if (marketDataJob != null) {
            marketDataJob.processMarketData(marketData);
            // MarketData doesn't use object pooling, so no return needed
        }
    }
}
