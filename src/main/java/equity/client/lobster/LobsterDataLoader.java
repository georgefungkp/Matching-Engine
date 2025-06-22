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

public class LobsterDataLoader {
    private static final Logger log = LogManager.getLogger(LobsterDataLoader.class);
    private static final int recordsToBeLoaded = 200;

    public static void main(String[] args) throws IOException {
        String fileName = "00001_2012-06-21_34200000_57600000_message_10.csv";
        String filePath = "data sources/lobster/";
//        String fileName = "00001_message.csv";
//        String filePath = "data sources/00001/";
        String stockSymbol = fileName.split("_")[0];
        String brokerID = "LOBSTER";

        processLobsterFile(Paths.get(filePath, fileName), stockSymbol, brokerID);
    }

    private static void processLobsterFile(Path filePath, String stockSymbol, String brokerID) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath.toFile()))) {
            String line;
            log.info("Processing file {}", filePath.getFileName());
            int noOfSucessfulRec = 0;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split(",");
                if (fields.length != 6)
                    continue;
                try {
                    // Parse fields
                    int type = Integer.parseInt(fields[1]);
                    String orderId = fields[2];
                    int size = Integer.parseInt(fields[3]);
                    BigDecimal price = new BigDecimal(fields[4])
                            .divide(BigDecimal.valueOf(10000), 4, RoundingMode.HALF_UP);
                    int direction = Integer.parseInt(fields[5]);

                    // Only process new limit orders (type 1)
                    if (type != 1) continue;

                    // Create an order message
                    String message = createOrderMessage(
                            stockSymbol,
                            brokerID,
                            orderId,
                            OrderType.LIMIT.value, // Order type
                            direction == 1 ? Side.BUY.value : Side.SELL.value, // Buy/Sell
                            price.toString(),
                            String.valueOf(size)
                    );

                    // Send to server
                    log.debug(sendMessageToServer(message));
                    noOfSucessfulRec++;
                    // Stop processing after N records
                    if (noOfSucessfulRec == recordsToBeLoaded)
                        break;
                } catch (NumberFormatException e) {
                    System.err.println("Error processing line: " + line);
                }
            }
            log.info("Complete processing file {}", filePath.getFileName());
        }catch (Exception ex){
            ex.printStackTrace();
        }
    }


    private static String sendMessageToServer(String message) throws IOException {
        try(Socket client = new Socket(dotenv.get("server"), Integer.parseInt(Objects.requireNonNull(dotenv.get("port_number"))))){
            return Client.sendMessageToServer(client, message);
        }
    }

    private static String createOrderMessage(String stockNo, String brokerId, String orderId,
                                          String orderType, String buySell, String price, String quantity) {
        return String.join(":", stockNo, brokerId, orderId, orderType, buySell, price, quantity);
    }

}
