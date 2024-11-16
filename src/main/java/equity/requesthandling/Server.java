package equity.requesthandling;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;

import equity.externalparties.MarketDataJob;
import equity.externalparties.ResultingTradeJob;
import equity.orderprocessing.OrderProcessingJob;
import equity.queue.QueueFactory;
import equity.queue.QueueType;
import equity.vo.MarketData;
import equity.vo.OrderRequest;
import equity.vo.Trade;

public class Server extends Thread{
	private LinkedBlockingQueue<OrderRequest> orderQueue;
	private LinkedBlockingQueue<MarketData> markerDataQueue;
	private LinkedBlockingQueue<Trade> resultingTradeQueue;
	
	static {
		int noOfAvaialbeThreads = Runtime.getRuntime().availableProcessors();
		System.out.println("No of avaialbe threas in this machine is " + noOfAvaialbeThreads);
	}

	public Server (LinkedBlockingQueue<OrderRequest> orderQueue, LinkedBlockingQueue<MarketData> markerDataQueue, LinkedBlockingQueue<Trade> resultingTradeQueue) {
		this.orderQueue = orderQueue;
		this.markerDataQueue = markerDataQueue;
		this.resultingTradeQueue = resultingTradeQueue;
		
	}
	
	public void createThreads() {
		new Thread(new OrderProcessingJob(orderQueue, markerDataQueue, resultingTradeQueue)).start();
		new Thread(new MarketDataJob(markerDataQueue)).start();
		new Thread(new ResultingTradeJob(resultingTradeQueue)).start();		
	}

	public void processingRequest() {
		while (true) {
			try {
//				System.out.println("Queue1:" + orderQueue);
				ServerSocket serverSocket = new ServerSocket(8080);
				System.out.println("Waiting for client on port " + serverSocket.getLocalPort() + "...");
				Socket server = serverSocket.accept();
				System.out.println("Just connected to " + server.getRemoteSocketAddress());
				DataInputStream in = new DataInputStream(server.getInputStream());
				DataOutputStream out = new DataOutputStream(server.getOutputStream());
				String value = in.readUTF();
//				System.out.println(value);
				try {
//					System.out.println("Queue2:" + orderQueue);
//					synchronized(orderQueue) {
//						orderQueue.add(value);
////						orderQueue.add("00001:002:L:B:10.1:100");
//					}
					String[] tokens = value.split(":");
					//Stock No: Broker ID: Order Type: B/S: Price: Quantity
					OrderRequest order = new OrderRequest(tokens[0], tokens[1], tokens[2], tokens[3], new BigDecimal(tokens[4]), Integer.valueOf(tokens[5]));
					orderQueue.put(order);
					System.out.println("Order Queue:" + orderQueue);
					out.writeUTF(server.getLocalSocketAddress() + " is processing your order. Good Luck!!!");
				} catch (Exception e) {
					e.printStackTrace();
					out.writeUTF(server.getLocalSocketAddress() + " has error. Order is rejected.");
				} finally {
					server.close();
					serverSocket.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(-1);
			}
		}

	}

	public void run() {
		this.processingRequest();
	}
	
	public static void main(String[] args) {
		LinkedBlockingQueue<OrderRequest> orderQueue = (LinkedBlockingQueue<OrderRequest>) new QueueFactory().getQueue(QueueType.PROCESSING_ORDER);
		LinkedBlockingQueue<MarketData> markerDataQueue = (LinkedBlockingQueue<MarketData>) new QueueFactory().getQueue(QueueType.MARKET_DATA);
		LinkedBlockingQueue<Trade> resultingTradeQueue = (LinkedBlockingQueue<Trade>) new QueueFactory().getQueue(QueueType.RESULTING_TRADE);
		
		Server server = new Server(orderQueue, markerDataQueue, resultingTradeQueue);
		server.createThreads();
		server.start();
	
//		try {
//			Thread.currentThread().sleep(1000);
////			orderQueue.put("00001:002:L:B:10.1:100");
////			orderQueue.put("00002:002:L:B:10.1:100");
////			Thread.currentThread().sleep(1000);
////			System.out.println(orderQueue);	
//		} catch (InterruptedException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}

//		server.processingRequest(orderQueue);

	}

}
