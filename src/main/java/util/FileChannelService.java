package util;

import equity.externalparties.MarketDataJob;
import equity.objectpooling.MarketData;
import equity.objectpooling.Order;
import equity.objectpooling.Trade;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.TreeMap;

public class FileChannelService {
    private static final Logger log = LogManager.getLogger(MarketDataJob.class);

    public void writeTradeToFile(Trade tradeData, Path path) throws IOException {
        // Stock No, Bid Broker Id, Bid Order id, Sell Broker Id, Sell order id, Executed Price, Qty, Executed Time\
        String message = String.format("%s,%s,%s,%s,%s,%s %s %s\r\n",
                tradeData.getStockNo(),
                tradeData.getBuyBrokerID(),
                tradeData.getBuyOrderID(),
                tradeData.getSellBrokerID(),
                tradeData.getSellOrderID(),
                tradeData.getExecutedPrice(),
                tradeData.getExecutedQty(),
                tradeData.getExecutionDateTime());

        FileLock lock;
        try (FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            lock = fileChannel.lock();
            if (fileChannel.write(ByteBuffer.wrap(message.getBytes())) == 0) {
                log.error("Cannot write log to {}", path);
            }
            lock.release();
        }
    }

    public void writeMarketDataToFile(MarketData data, Path path) throws IOException {
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
        try (FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            lock = fileChannel.lock();
            if (fileChannel.write(ByteBuffer.wrap(message.getBytes())) == 0)
                log.error("Cannot write log to {}", path);
            lock.release(); // manually release the lock
        }

    }

    public StringBuilder orderBookToTxt(TreeMap<Double, LinkedList<Order>> orderBook) {
        StringBuilder message = new StringBuilder();
        for (Entry<Double, LinkedList<Order>> entry : orderBook.entrySet()) {
            for (Order order : entry.getValue()) {
                message.append(order.getBrokerID()).append("-").append(order.getClientOrdID()).append(" ").append(order.getPrice()).append(" ").append(order.getQuantity()).append("\n");
            }
        }
        return message;
    }
}