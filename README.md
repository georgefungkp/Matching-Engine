# Matching-Engine Project

## Developer Information

**Name:** George Fung

**Email:** georgefungkp@gmail.com

**GitHub Username:** georgefungkp

**LinkedIn Profile:** https://www.linkedin.com/in/george-fung

## Table of Contents
- [Developer Information](#developer-information)
- [Introduction](#introduction)
- [Features](#features)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Time Complexity of Main Operations](#time-complexity-of-main-operations)
- [Performance Profiling Report](#performance-profiling-report)
  - [Garbage Collector Performance Analysis](#garbage-collector-performance-analysis)
  - [Test Environment Specifications](#test-environment-specifications)
  - [Market Data Specifications](#market-data-specifications)
  - [Garbage Collector Configurations](#garbage-collector-configurations)
    - [G1GC VS ZGC](#g1gc-vs-zgc)
    - [Enable Generation in ZGC](#enable-generation-in-zgc)
- [Design Consideration](#design-consideration)
  - [Design Patterns](#design-patterns)
- [Message Cycle of FIX in a Trade](#message-cycle-of-fix-in-a-trade)

## Introduction

This project implements a Matching Engine, a key component in trading platforms and exchanges, responsible for handling buying and selling orders and matching them based on specific logic. The project is developed using Java SDK version 22 and helps understand the working of real-world trade execution.

## Features

 - Efficient order handling and order book maintenance
 - Supports **limit** as well as **market** orders
 - Real-time matching logic. When best_bid >= best_ask, the trade executes at the _ask price_ (lower price)
 - After order matching, trade and market data are sent to Client. 
 - Extensible and maintainable code
 - Supports internal message format as well as FIX message format
 - High-performance logging with Log4j2 Async Logger

## Getting Started
![Diagram](images/Matching%20Engine%20Component%20Diagram.png)
### Prerequisites

List here the prerequisites and links to the installation procedure of each:

- [Java SDK](https://www.oracle.com/java/technologies/downloads/)
- An IDE of your choice (Although this project was developed using Intellij IDEA)

### Time Complexity of main operations

| Operation    | Time Complexity          |
|:-------------|:-------------------------|
| Place Order  | O(log n)                 |
| Cancel Order | O(1)                     |
| Match Order  | O(1)                     |
| Amend Order  | Qty O(1), Price O(log n) |
| Search Order | O(1)                     |

## Performance Profiling Report

### Garbage Collector Performance Analysis
### Test Environment Specifications
- Hardware:
  * CPU: Intel 12th Gen Core i7-12700F
  * Memory: 16GB DDR4
- Software:
  * Java Runtime: OpenJDK 22
  * OS: Linux Kernel 6.4
  * JVM Build: 22+36-2088

### Market Data Specifications
- Source: LOBSTER (Level II Market Data)
- Exchange: NASDAQ
- Trading Session: June 21, 2012 (09:30 - 16:00 ET)
- Test Securities:
  * Apple Inc. (AAPL)
  * Amazon.com Inc. (AMZN)

### Garbage Collector Configurations
#### G1GC VS ZGC 
G1GC Configuration
```properties
-XX:+UseG1GC -Xms18g -Xmx18g -XX:ConcGCThreads=12 -XX:+AlwaysPreTouch
```
ZGC Configuration 1
```properties
-XX:+UseZGC -Xms18g -Xmx18g -XX:ConcGCThreads=12 -XX:+AlwaysPreTouch
```
ZGC Configuration 2
```properties
-XX:+UseZGC -Xms18g -Xmx18g -XX:ConcGCThreads=12 -XX:+AlwaysPreTouch \
  -XX:+ZGenerational -XX:ZAllocationSpikeTolerance=2.0 -XX:MaxGCPauseMillis=1 -XX:ParallelGCThreads=16 -XX:ZCollectionInterval=5 -XX:ZFragmentationLimit=10
```

| Symbol   | Order Volume | Execution Rate | G1GC (ms) | ZGC - config 1 (ms) | ZGC - config 2 (ms) |
|:---------|:-------------|:---------------|:----------|:--------------------|:--------------------|
| AAPL     | 27,843       | 89.36%         | 27,767    | 28,367              | 27,661              |
| AMZN     | 131,954      | 80.59%         | 132,196   | 220,101             | 136,869             |

Performance Analysis
- Under the similar VM options, the performance of G1GC is better than ZGC (esp on large dataset).
- ZGC's design optimizations become more apparent with larger heap sizes (>100GB)
- Smaller heap sizes require careful ZGC parameter tuning for optimal performance (30% difference)
 
Conclusions
The performance analysis reveals that while G1GC provides better out-of-the-box performance for medium-sized heaps, ZGC can achieve superior performance through careful tuning. The 31.8% performance improvement in ZGC through optimization demonstrates its potential, despite not operating in its optimal large-heap environment (>100GB).

#### Enable Generation in ZGC
ZGC Configuration 1
```properties
-XX:+UseZGC -Xms18g -Xmx18g -XX:ConcGCThreads=12 -XX:+AlwaysPreTouch -XX:ZAllocationSpikeTolerance=1.5 \
-XX:MaxGCPauseMillis=1 -XX:ParallelGCThreads=16 -XX:ZCollectionInterval=3 -XX:ZFragmentationLimit=10 -XX:ZUncommitDelay=3
```
ZGC Configuration 2
```properties
-XX:+UseZGC -Xms18g -Xmx18g **-XX:+ZGenerational** -XX:ConcGCThreads=12 -XX:+AlwaysPreTouch -XX:ZAllocationSpikeTolerance=1.5 \
-XX:MaxGCPauseMillis=1 -XX:ParallelGCThreads=16 -XX:ZCollectionInterval=3 -XX:ZFragmentationLimit=10 -XX:ZUncommitDelay=3
```
| Symbol   | Order Volume | ZGC - config 1 (ms) | ZGC - config 2 (ms) |
|:---------|:-------------|:--------------------|:--------------------|
| AAPL     | 27,843       | 33,601              | 27,304              |
| AMZN     | 131,954      | 141,434             | 135,544             |

Performance Analysis
- **ZGC Configuration 2 (with generational mode enabled)** consistently outperformed Configuration 1 across all test cases.  
- Enabling generational garbage collection reduced execution time significantly, especially for **AAPL**, by approximately **18.7%** compared to Configuration 1.
- Generational garbage collection helps optimize memory usage by categorizing objects based on their lifecycle (short-lived vs. long-lived), reducing overall pause times and improving throughput.

Conclusions
- Leveraging ZGC's generational mode can provide **substantial performance gains**, particularly for scenarios with moderate to high memory churn.
- The smaller improvement in **AMZN's** execution time suggests that additional fine-tuning of generational parameters may be required for workloads with larger datasets or higher order volumes.
- This analysis highlights the importance of generational garbage collection for improving responsiveness and throughput in high-frequency trading systems.

## Design Consideration
- The time complexity of order handling is shown as below:

| Operation               | PriorityQueue(Binary Heap) | TreeMap(Red-black Tree)    |
|:------------------------|:---------------------------|:---------------------------|
| Insert                  | O(log n)                   | O(log n)                   |  
| Peek (Best Bid/Ask)     | O(1) (top element)         | O(log n) (firstKey()/lastKey() |
| Remove                  | O(n)                       | O(log n)                   |
| Search by Price         | O(n)                       | O(log n)                   |
| Iterate in Sorted Order | O(n log n)                 | O(n)                       |

- The data structure of order book is <b> Tree map </b> with <b> LinkedList </b>. So, the time complexity of a new limit price is O(log n). For cancellation, it's also O(log n). On average, Tree map is the best choice in JDK implementation.
<img src="images/PQvsTreeMap.jpg" width="500" height="200">
- In the single-thread environment, TreeMap ensures the performance is O(log n). However, TreeMap may not be the best choice in the concurrency. It's not thread-safe and developer has to handle race conditions.
So, I choose to use <b>ConcurrenctSkipListMap</b> to replace TreeMap as it is good for individual atomic operations. All basic operations (put, get, remove) are thread-safe by design 
so that it provides atomicity for single operations.
<img src="images/TreeMapvsConcurrentSkipListMap.jpg" width="500" height="200">
- I use **AsyncLogger + synchronous appenders** for best end-to-end performance. **AsyncAppender** may be used for particularly slow/risky sinks (e.g., network/syslog).
- Use Hashmap to record order object reference so that it's easy to amend or cancel the order. 
- Order object pool is created so that we can minimize the number of objects and times of GC in the memory, and reduce latency of order creation.
- Difference exchanges give different priority to market order. In this design, the market order is treated as the best available order and is executed first.

### Asynchronous Logging (Log4j2)
This project can use Log4j2 Async Loggers for low-latency, high-throughput logging. Async logging offloads message formatting and I/O to background threads using the LMAX Disruptor ring buffer.

### Design Patterns
I have applied several common design patterns in my system design. 
1. **Object Pool Pattern**
    - Clearly implemented through `OrderPoolManager``OrderObjectPool``TradeObjectPool`
    - Used to manage and reuse Order and Trade objects
    - Helps reduce the overhead of creating new objects by recycling unused ones

2. **Singleton Pattern**
    - Used in with static initialization and methods `OrderPoolManager`
    - Ensures single instances of order and trade pools per stock
    - Manages the lifecycle of order and trade objects globally

3. **Factory Method Pattern**
    - Implemented in with methods like and `OrderPoolManager``requestOrderObj``requestTradeObj`
    - Encapsulates object creation logic while maintaining the object poo

4. **Observer Pattern**
    - The system uses multiple queues: `orderQueue``marketDataQueue``resultingTradeQueue`
    - Different components observe and process these queues

5. **Command Pattern**
    - Orders represent commands that need to be executed
    - Each order encapsulates all necessary information for processing
    - Different types of orders (Market, Limit) can be processed differently

6. **Strategy Pattern**
    - Different order types (MARKET, LIMIT) suggest different execution strategies
    - Order matching can be performed using different strategies based on an order type


## Order Execution Life Cycle
Order Initiation → Order Routing → Execution -> Trade Capture → Clearing → Settlement → Reconciliation/Reporting  

#### Example Workflow Summary:
Logon(Logon (A)) → 2. New Order(New Order Single (D)) → 3. Order Ack(Execution Report (8) with ExecType=0)
→ 4. Execution Report(Execution Report (8) with ExecType=F) → 5. Trade Capture Report(Trade Capture Report (AE))
→ 6. Allocation(Allocation Instruction (AS)) → 7. Settlement(Settlement Instructions (T)) → 8. Logout(Logout (5))

