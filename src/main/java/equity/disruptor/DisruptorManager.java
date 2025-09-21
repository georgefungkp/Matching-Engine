// DisruptorManager.java
package equity.disruptor;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;
import equity.objectpooling.*;
import equity.disruptor.events.GenericReference;
import equity.disruptor.handlers.GenericReferenceHandler;

public class DisruptorManager {
    private static final int RING_BUFFER_SIZE = 1024;
    
    private final Disruptor<GenericReference<Order>> orderDisruptor;
    private final RingBuffer<GenericReference<Order>> orderRingBuffer;
    
    private final Disruptor<GenericReference<Trade>> tradeDisruptor;
    private final RingBuffer<GenericReference<Trade>> tradeRingBuffer;
    
    private final Disruptor<GenericReference<MarketData>> marketDataDisruptor;
    private final RingBuffer<GenericReference<MarketData>> marketDataRingBuffer;
    
    public DisruptorManager() {
        orderDisruptor = new Disruptor<>(GenericReference::new, RING_BUFFER_SIZE, DaemonThreadFactory.INSTANCE);
        orderRingBuffer = orderDisruptor.getRingBuffer();
        
        tradeDisruptor = new Disruptor<>(GenericReference::new, RING_BUFFER_SIZE, DaemonThreadFactory.INSTANCE);
        tradeRingBuffer = tradeDisruptor.getRingBuffer();
        
        marketDataDisruptor = new Disruptor<>(GenericReference::new, RING_BUFFER_SIZE, DaemonThreadFactory.INSTANCE);
        marketDataRingBuffer = marketDataDisruptor.getRingBuffer();
    }
    
    public void start() {
        orderDisruptor.start();
        tradeDisruptor.start();
        marketDataDisruptor.start();
    }
    
    public void shutdown() {
        orderDisruptor.shutdown();
        tradeDisruptor.shutdown();
        marketDataDisruptor.shutdown();
    }
    
    // Single method to set up all handlers with the same handler instance
    public void setEventHandlers(GenericReferenceHandler<Order> orderHandler,
                                GenericReferenceHandler<Trade> tradeHandler,
                                GenericReferenceHandler<MarketData> marketDataHandler) {
        orderDisruptor.handleEventsWith(orderHandler);
        tradeDisruptor.handleEventsWith(tradeHandler);
        marketDataDisruptor.handleEventsWith(marketDataHandler);
    }
    
    // Or even simpler - use the same handler for all (since it handles all types internally)
    public void setEventHandler(GenericReferenceHandler<?> handler) {
        orderDisruptor.handleEventsWith((GenericReferenceHandler<Order>) handler);
        tradeDisruptor.handleEventsWith((GenericReferenceHandler<Trade>) handler);
        marketDataDisruptor.handleEventsWith((GenericReferenceHandler<MarketData>) handler);
    }
    
    // Publish methods remain the same
    public void publishOrder(Order order) {
        long sequence = orderRingBuffer.next();
        try {
            GenericReference<Order> event = orderRingBuffer.get(sequence);
            event.setRef(order);
        } finally {
            orderRingBuffer.publish(sequence);
        }
    }
    
    public void publishTrade(Trade trade) {
        long sequence = tradeRingBuffer.next();
        try {
            GenericReference<Trade> event = tradeRingBuffer.get(sequence);
            event.setRef(trade);
        } finally {
            tradeRingBuffer.publish(sequence);
        }
    }
    
    public void publishMarketData(MarketData marketData) {
        long sequence = marketDataRingBuffer.next();
        try {
            GenericReference<MarketData> event = marketDataRingBuffer.get(sequence);
            event.setRef(marketData);
        } finally {
            marketDataRingBuffer.publish(sequence);
        }
    }
}
