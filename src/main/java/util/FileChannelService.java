package util;

import equity.objectpooling.MarketData;
import equity.objectpooling.Trade;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;

public class FileChannelService {
    private static final Logger log = LogManager.getLogger(FileChannelService.class);

    public int writeTradeToFile(Trade tradeData, Path path) throws IOException {
        // Trade seq ID, Stock No, Bid Broker ID, Bid Order ID, Sell Broker ID, Sell order IDs, Executed Price, Qty, Executed Time\
        String message = String.format("%s, %s,%s,%s,%s,%s,%s,%s,%s,%s,%s\r\n",
                tradeData.getTradeSeqNo(),
                tradeData.getStockNo(),
                tradeData.getBuyBrokerID(),
                tradeData.getBuyOrderID(),
                tradeData.getInternalBuyOrderSeqNo(),
                tradeData.getSellBrokerID(),
                tradeData.getSellOrderID(),
                tradeData.getInternalSellOrderSeqNo(),
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

    public int writeMarketDataToFile(@NotNull MarketData data, Path path) throws IOException {
        String bestBidTxt = (data.bestBid() == null) ? "" : data.bestBid().toString();
        String bestAskTxt = (data.bestAsk() == null) ? "" : data.bestAsk().toString();
        String lastTradePrice = (data.lastTradePrice() == null) ? "" : data.lastTradePrice().toString();
        String message = "Publish Date Time:" + ZonedDateTime.now()
                + "Stock Name:" + data.stockNo() + "\n"
                + "Best Bid Price:" + bestBidTxt + "\n"
                + "Best Ask Price:" + bestAskTxt + "\n"
                + "Last Trade Price:" + lastTradePrice + "\n"
                + "Bid orders\n" + data.bidMapOrdersStr()
                + "Ask orders\n" + data.askMapOrdersStr();

        FileLock lock;
        int noOfBytes;
        try (FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            lock = fileChannel.lock();
            noOfBytes = fileChannel.write(ByteBuffer.wrap(message.getBytes()));
            lock.release(); // manually release the lock
        }
        return noOfBytes;
    }


}