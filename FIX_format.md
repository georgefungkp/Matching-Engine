## Common FIX Tags Reference

### Header Tags
| Tag | Name            | Description |
|----:|-----------------|-------------|
|   8 | BeginString     | Identifies beginning of FIX message (e.g., "FIX.4.2") |
|   9 | BodyLength      | Byte count of message body (from tag 35 onward) |
|  35 | MsgType         | Type of FIX message (e.g., "D"=New Order, "8"=Execution Report) |
|  34 | MsgSeqNum       | Sequence number of message |
|  49 | SenderCompID    | Sender's company identifier |
|  56 | TargetCompID    | Target company identifier |
|  52 | SendingTime     | Time message was sent (UTC) |
|  57 | TargetSubID     | Target sub-identifier (used for routing) |

### Order-Related Tags
|   Tag | Name            | Description |
|------:|-----------------|-------------|
|     1 | Account         | Account identifier |
|    11 | ClOrdID         | Unique identifier for order as assigned by institution |
|    37 | OrderID         | Unique identifier for order as assigned by broker/exchange |
|   ⚡38 | OrderQty        | Quantity ordered |
|   ⚡40 | OrdType         | Order type (e.g., "1"=Market, "2"=Limit) |
|   ⚡44 | Price           | Price per unit of quantity |
|   ⚡54 | Side            | Side of order (1=Buy, 2=Sell, etc.) |
|   ⚡55 | Symbol          | Security identifier (ticker symbol) |
|    59 | TimeInForce     | Specifies how long the order remains active (e.g., "0"=Day, "1"=GTC) |

### Execution Tags
|   Tag | Name            | Description |
|------:|-----------------|-------------|
|    ⚡6 | AvgPx           | Calculated average price of all fills on this order |
|   ⚡14 | CumQty          | Total quantity filled |
|    17 | ExecID          | Unique identifier for execution |
|    20 | ExecTransType   | Execution transaction type (0=New, 1=Cancel, 2=Correct) |
|   ⚡39 | OrdStatus       | Order status (0=New, 2=Filled, 4=Canceled, 8=Rejected) |
|   150 | ExecType        | Execution type (0=New, 4=Canceled, F=Trade) |
|  ⚡151 | LeavesQty       | Quantity remaining to be executed |

### Additional Common Tags
|  Tag | Name            | Description |
|-----:|-----------------|-------------|
|   10 | CheckSum        | Three byte checksum of message (mod 256) |
|   15 | Currency        | Currency code (ISO 4217) |
|   18 | ExecInst        | Execution instructions (e.g., "6"=Pegged) |
|   21 | HandlInst       | Handling instructions (1=Auto, 2=Manual) |
|   22 | IDSource        | Identifier source for SecurityID (1=CUSIP, 4=ISIN, etc.) |
|   48 | SecurityID      | Security identifier (alternative to Symbol) |
|   58 | Text            | Free format text string |
|   60 | TransactTime    | Time of execution/transaction (UTC) |
|  100 | ExDestination   | Execution destination (exchange/market) |

> **Note:** This table covers common FIX 4.2 tags. For complete specifications, refer to the official [FIX Protocol documentation](http://www.fixtrading.org/).