package equity.client;

import equity.objectpooling.Order;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static util.ReadConfig.dotenv;

/**
 * Client class to demonstrate connecting to a server, sending order requests, and receiving responses.
 */
public class Client {

	private static final Logger log = LogManager.getLogger(Client.class);
	public static ExecutorService executor = Executors.newFixedThreadPool(5);

	public static void main(String[] args) {
		for (int i = 0; i < 100; i++) {
			int finalI = i;
			executor.execute(() -> {
				try {
					Socket client = new Socket(dotenv.get("server"), Integer.parseInt(Objects.requireNonNull(dotenv.get("port_number"))));
                    log.debug("Thread {} connected to {} on port {}", Thread.currentThread().getName(), dotenv.get("server"), dotenv.get("port_number"));
					final String response = sendOrderToServer(client, RandomOrderRequestGenerator.getNewLimitOrder("00001", String.format("%03d", finalI), null, null, null, null));

					log.debug(response);
//					Thread.sleep(1000);
					client.close();

				} catch (IOException e) {
					log.error(e);
				}
			});
		}

		executor.shutdown();
		try {
			executor.awaitTermination(10, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
		}

	}

	private static @NotNull String sendOrderToServer(Socket client, Order order) throws IOException {
		//Stock No: Broker ID: Order Type: B/S: Price: Quantity
		String message = order.getStockNo() + ":" + order.getBrokerID() + ":" + order.getClientOrdID() + ":"
				+ order.getOrderType() + ":" + order.getBuyOrSell() + ":"
				+ order.getPrice().get() + ":" + order.getQuantity();
		return sendMessageToServer(client, message);
//					message = "00001:003:L:B:8.1:500";
//					message = "00001:003:L:B:8.2:400";
//		message = "00001:001:M:S:8.2:10000";
	}

	public static @NotNull String sendMessageToServer(Socket client, String message) throws IOException {
		DataOutputStream outToServer = new DataOutputStream(client.getOutputStream());
		outToServer.writeBytes(message + '\n');
//		outToServer.writeUTF(message);
		outToServer.flush();
		DataInputStream inFromServer = new DataInputStream(client.getInputStream());
		return inFromServer.readUTF();
	}

}
