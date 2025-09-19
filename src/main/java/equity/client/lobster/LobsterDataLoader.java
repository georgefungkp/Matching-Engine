package equity.client.lobster;

import equity.client.Client;
import equity.objectpooling.Order.Side;
import equity.objectpooling.Order.OrderType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import static util.ReadConfig.dotenv;

/**
 * Loads and processes LOBSTER market data files, converting them to order messages
 * and sending them to the trading server.
 */
public class LobsterDataLoader {
    private static final Logger log = LogManager.getLogger(LobsterDataLoader.class);
    private static final BigDecimal PRICE_DIVISOR = BigDecimal.valueOf(10_000);
    private static final int PRICE_SCALE = 4;
    private static final int NEW_LIMIT_ORDER_TYPE = 1;
    private static final int BUY_DIRECTION = 1;
    private static final int EXPECTED_FIELD_COUNT = 6;
    
    private final String serverHost;
    private final int serverPort;
    private final int recordLimit;

    public LobsterDataLoader() {
        this(-1); // No limit by default
    }
    
    public LobsterDataLoader(int recordLimit) {
        this.serverHost = dotenv.get("server");
        this.serverPort = Integer.parseInt(Objects.requireNonNull(dotenv.get("port_number")));
        this.recordLimit = recordLimit;
    }

    public static void main(String[] args) throws IOException {
        LobsterConfiguration config = new LobsterConfiguration(
            "APPL_2012-06-21_34200000_57600000_message_10.csv",
            "data sources/lobster/"
        );
        
        LobsterDataLoader loader = new LobsterDataLoader();
        loader.processFile(config);
    }

    /**
     * Processes a LOBSTER data file and sends orders to the server.
     */
    public void processFile(LobsterConfiguration config) {
        Path fullPath = Paths.get(config.getFilePath(), config.getFileName());
        
        try (BufferedReader reader = new BufferedReader(new FileReader(fullPath.toFile()))) {
            log.info("Processing file: {}", fullPath.getFileName());
            
            int successfulRecords = processFileLines(reader, config);
            
            log.info("Completed processing file: {} - {} records processed", 
                    fullPath.getFileName(), successfulRecords);
                    
        } catch (IOException e) {
            log.error("Error processing file: {}", fullPath, e);
            throw new RuntimeException("Failed to process LOBSTER file", e);
        }
    }

    private int processFileLines(BufferedReader reader, LobsterConfiguration config) throws IOException {
        String line;
        int successfulRecords = 0;
        
        while ((line = reader.readLine()) != null && !isLimitReached(successfulRecords)) {
            if (processLine(line, config)) {
                successfulRecords++;
            }
        }
        
        return successfulRecords;
    }

    private boolean processLine(String line, LobsterConfiguration config) {
        try {
            LobsterRecord record = parseLine(line);
            if (record == null || !record.isNewLimitOrder()) {
                return false;
            }

            String orderMessage = createOrderMessage(record, config);
            String response = sendMessageToServer(orderMessage);
            log.debug("Server response: {}", response);
            
            return true;
            
        } catch (Exception e) {
            log.warn("Error processing line: {} - {}", line, e.getMessage());
            return false;
        }
    }

    private LobsterRecord parseLine(String line) {
        String[] fields = line.split(",");
        if (fields.length != EXPECTED_FIELD_COUNT) {
            return null;
        }

        try {
            return new LobsterRecord(
                Integer.parseInt(fields[1]), // type
                fields[2],                   // orderId
                Integer.parseInt(fields[3]), // size
                parsePrice(fields[4]),       // price
                Integer.parseInt(fields[5])  // direction
            );
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal parsePrice(String priceString) {
        return new BigDecimal(priceString)
            .divide(PRICE_DIVISOR, PRICE_SCALE, RoundingMode.HALF_UP);
    }

    private String createOrderMessage(LobsterRecord record, LobsterConfiguration config) {
        return String.join(":", 
            config.getStockSymbol(),
            config.getBrokerID(),
            record.orderId(),
            OrderType.LIMIT.value,
            record.isBuyOrder() ? Side.BUY.value : Side.SELL.value,
            record.price().toString(),
            String.valueOf(record.size())
        );
    }

    private String sendMessageToServer(String message) throws IOException {
        try (Socket client = new Socket(serverHost, serverPort)) {
            return Client.sendMessageToServer(client, message);
        }
    }

    private boolean isLimitReached(int processedRecords) {
        return recordLimit > 0 && processedRecords >= recordLimit;
    }

    /**
     * Configuration class for LOBSTER data processing.
     */
    public static class LobsterConfiguration {
        private final String fileName;
        private final String filePath;
        private final String stockSymbol;
        private final String brokerID;

        public LobsterConfiguration(String fileName, String filePath) {
            this.fileName = fileName;
            this.filePath = filePath;
            this.stockSymbol = extractStockSymbol(fileName);
            this.brokerID = "LOBSTER";
        }

        private String extractStockSymbol(String fileName) {
            return fileName.split("_")[0];
        }

        public String getFileName() { return fileName; }
        public String getFilePath() { return filePath; }
        public String getStockSymbol() { return stockSymbol; }
        public String getBrokerID() { return brokerID; }
    }

        /**
         * Represents a parsed LOBSTER record.
         */
        private record LobsterRecord(int type, String orderId, int size, BigDecimal price, int direction) {

        public boolean isNewLimitOrder() {
                return type == NEW_LIMIT_ORDER_TYPE;
            }

            public boolean isBuyOrder() {
                return direction == BUY_DIRECTION;
            }
        }
}
