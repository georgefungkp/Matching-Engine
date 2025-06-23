package util;

import equity.objectpooling.MarketData;
import equity.objectpooling.Order;
import equity.objectpooling.Trade;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.AbstractMap;
import java.util.LinkedList;
import java.util.Map.Entry;

public class FileChannelService {
    private static final Logger log = LogManager.getLogger(FileChannelService.class);
    private static final SequenceGenerator TRADE_ID_GENERATOR = new SequenceGenerator();

    public int writeTradeToFile(Trade tradeData, Path path) throws IOException {
        // Trade seq ID, Stock No, Bid Broker ID, Bid Order ID, Sell Broker ID, Sell order IDs, Executed Price, Qty, Executed Time\
        String message = String.format("%s, %s,%s,%s,%s,%s,%s %s %s\r\n",
                TRADE_ID_GENERATOR.getNextSequence(),
                tradeData.getStockNo(),
                tradeData.getBuyBrokerID(),
                tradeData.getBuyOrderID(),
                tradeData.getSellBrokerID(),
                tradeData.getSellOrderID(),
                tradeData.getExecutedPrice(),
                tradeData.getExecutedQty(),
                tradeData.getExecutionDateTime());
        FileLock lock;
        int noOfBytes;
        try (FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            lock = fileChannel.lock();
            noOfBytes = fileChannel.write(ByteBuffer.wrap(message.getBytes()));
            lock.release();
        }
        return noOfBytes;
    }

    public int writeMarketDataToFile(MarketData data, Path path) throws IOException {
        String bestBidTxt = (data.bestBid() == null) ? "" : data.bestBid().toString();
        String bestAskTxt = (data.bestAsk() == null) ? "" : data.bestAsk().toString();
        String lastTradePrice = (data.lastTradePrice() == null) ? "" : data.lastTradePrice().toString();
        String message = "Stock Name:" + data.stockNo() + "\n"
                + "Best Bid Price:" + bestBidTxt + "\n"
                + "Best Ask Price:" + bestAskTxt + "\n"
                + "Last Trade Price:" + lastTradePrice + "\n"
                + "Bid orders\n" + orderBookToTxt(data.bidMap())
                + "Ask orders\n" + orderBookToTxt(data.askMap());

        FileLock lock;
        int noOfBytes;
        try (FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            lock = fileChannel.lock();
            noOfBytes = fileChannel.write(ByteBuffer.wrap(message.getBytes()));
            lock.release(); // manually release the lock
        }
        return noOfBytes;
    }

    public StringBuilder orderBookToTxt(AbstractMap<BigDecimal, LinkedList<Order>> orderBook) {
        StringBuilder message = new StringBuilder();
        for (Entry<BigDecimal, LinkedList<Order>> entry : orderBook.entrySet()) {
            for (Order order : entry.getValue()) {
                message.append(order.getBrokerID()).append("-").append(order.getClientOrdID()).append(" ").append(order.getPrice().get()).append(" ").append(order.getRemainingQty()).append("\n");
            }
        }
        return message;
    }
}