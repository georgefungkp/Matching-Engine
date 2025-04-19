# Matching-Engine Project

## Developer Information

**Name:** George Fung

**Email:** georgefungkp@gmail.com

**GitHub Username:** georgefungkp

**LinkedIn Profile:** https://www.linkedin.com/in/george-fung

## Introduction

This project implements a Matching Engine, a key component in trading platforms and exchanges, responsible for handling buying and selling orders and matching them based on specific logic. The project is developed using Java SDK version 22 and helps understand the working of real-world trade execution.

## Features

 - Efficient order handling and order book maintenance
 - Supports **limit** as well as **market** orders
 - Real-time matching logic
 - Extensible and maintainable code
 - Supports internal message format as well as FIX message format

## Getting Started

### Prerequisites

List here the prerequisites and links to the installation procedure of each:

- [Java SDK](https://www.oracle.com/java/technologies/downloads/)
- An IDE of your choice (Although this project was developed using Intellij IDEA)

### Design consideration
1. The data structure of order book is <b> Tree map </b> with <b> Array </b>. So, the time complexity of a new limit price is O(log n), and first or last key is O(1) as Java always caches the leftmost/rightmost nodes. For cancellation, it's also O(log n). On average, Tree map is the best choice in JDK implementation.
![Why TreeMap?](https://github.com/georgefungkp/Matching-Engine/blob/main/PQvsTreeMap.jpg)


## Message cycle of FIX in a trade 
[The following is just for information. Not every message type is implemented in the project.]
First, the basic steps in a trade: order initiation, routing, execution, confirmation, and settlement. Each step corresponds to specific FIX messages. Let me list them in order.

1. **Logon (A)**: The session starts with a Logon message to establish the connection. Both parties exchange this to authenticate.

2. **New Order Single (D)**: The client sends a new order using the D message. It includes details like symbol, quantity, price, etc.

3. **Execution Report (8)**: The broker responds with an Execution Report. Initially, this might be an acknowledgment (Pending New), then updates as the order is filled.

4. **Order Cancel/Replace Request (G)**: If the client wants to modify the order, they send a G message.

5. **Order Cancel Request (F)**: To cancel an order, an F message is sent.

6. **Trade Capture Report (AE)**: Post-trade, this message confirms the details of the executed trade.

7. **Allocation (AS)**: For multi-account trades, allocations are sent to distribute the executed quantity.

8. **Confirmation (AK)**: Final confirmation of the trade details and settlement instructions.

9. **Logout (5)**: Ends the session

#### Example Workflow Summary:
Logon(Logon (A)) → 2. New Order(New Order Single (D)) → 3. Order Ack(Execution Report (8) with ExecType=0)
→ 4. Execution Report(Execution Report (8) with ExecType=F) → 5. Trade Capture Report(Trade Capture Report (AE))
→ 6. Allocation(Allocation Instruction (AS)) → 7. Settlement(Settlement Instructions (T)) → 8. Logout(Logout (5))

### TO-DO
Zero GC: 
1. Use object pooling and primitives (e.g. Apache Commons Pool)
Object pooling reuses objects instead of creating new ones, reducing GC pressure.
Primitives (e.g., int instead of Integer) avoid object creation, thus reducing heap allocation
2. StringBuffer instead of String
3. Use efficient collections (e.g., ArrayList instead of LinkedList when random access is needed).
4. Tune GC settings (e.g., use G1GC or ZGC for low-latency applications
5. Avoid False Sharing (e.g Use @Contended and/or Thread-Local) to reduce CPU Cache Invalidation
6. Message sharing: A circular buffer, for example, Disruptor, (or ring buffer) is a fixed-size, low-latency data structure optimized for real-time message processing in HFT. It allows zero-allocation, lock-free reads/writes, making it ideal for ultra-low-latency trading systems
7. Real-world compareAndSet (CAS) uses atomic CPU instructions (e.g., CMPXCHG on x86) to merge the check+update into one atomic operation:
8. -XX:+UseSerialGC # Single-threaded GC (for tiny heaps)
-XX:+UseNUMA # Optimize memory access
-XX:+AlwaysPreTouch # Pre-allocate heap pages
-Xms4G -Xmx4G # Fixed heap size
9. Leverage zero-GC libraries (Disruptor, Agrona). Aeron, Algoteq