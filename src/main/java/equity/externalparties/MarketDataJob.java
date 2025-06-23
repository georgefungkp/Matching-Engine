package equity.externalparties;

import equity.objectpooling.MarketData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import util.FileChannelService;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.LinkedBlockingQueue;

import static util.ReadConfig.dotenv;

public class MarketDataJob implements Runnable {
    private static final Logger log = LogManager.getLogger(MarketDataJob.class);
    private final LinkedBlockingQueue<MarketData> marketDataQueue;
    FileChannelService fileChannelService;
    private boolean listening = true;
    private boolean writeToFile = false;

    public MarketDataJob(LinkedBlockingQueue<MarketData> marketDataQueue, FileChannelService fileChannelService) {
        this.marketDataQueue = marketDataQueue;
        this.fileChannelService = fileChannelService;
    }

    /**
     * Continuously listens for market data updates from the marketDataQueue and writes the data to a file in a specific format.
     * The file will contain information such as stock name, best bID and ask prices, last trade price, and lists of bID and ask orders.
     * The file is named based on the stock number and the current date appended with a '.txt' extension.
     * If an exception occurs while processing the data or writing to the file, the method will log the error and stop listening for updates.
     */
    @Override
    public void run() {
        while (listening) {
            try {
                MarketData data = marketDataQueue.take();

                Path path = Paths.get(dotenv.get("marketData") + "_" + data.stockNo() + "_"
                        + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".txt");
                if (writeToFile && fileChannelService.writeMarketDataToFile(data, path) ==0)
                    log.error("Cannot write log to {}", path);
            } catch (InterruptedException | IOException e) {
                log.error(e);
                listening = false;
            }
        }
    }



}
