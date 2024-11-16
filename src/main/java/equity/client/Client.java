package equity.client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Client {

	public static ExecutorService executor = Executors.newFixedThreadPool(5);

	private static String serverName = "localhost";
	private static int port = 8080;

	public static void main(String[] args) {
		for (int i = 0; i < 1; i++) {
			executor.submit(() -> {
				try {
					OrderRequest order = new OrderRequest();
					Socket client = new Socket(serverName, port);
					System.out.println("Thread " + Thread.currentThread().getName() + " connected to " + serverName + " on port " + port);
					DataOutputStream outToServer = new DataOutputStream(client.getOutputStream());
					//Stock No: Broker ID: Order Type: B/S: Price: Quantity
					String message = order.getStockNo() + ":" + order.getBrokerId() + ":" + order.getOrderType() + ":" + order.getDirection() + ":"
							+ order.getPrice() + ":" + order.getQuantity();
//					message = "00001:003:L:B:8.1:500";
//					message = "00001:003:L:B:8.2:400";
					message = "00001:001:M:S:8.2:10000";
					outToServer.writeUTF(message);
					DataInputStream inFromServer = new DataInputStream(client.getInputStream());
					String response = new String(inFromServer.readUTF());
					
					System.out.println("r:" + response);
//					Thread.sleep(1000);
					client.close();

				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			});
		}

		executor.shutdown();
		try {
			executor.awaitTermination(10, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

}
