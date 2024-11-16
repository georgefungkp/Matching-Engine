package equity.vo;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.PriorityQueue;
import java.util.TreeMap;

public class MarketData {
    private String stockNo;
    private BigDecimal bestBid;
    private BigDecimal bestAsk;
    private BigDecimal nominalPrice;
    private TreeMap<BigDecimal, PriorityQueue<Order>> bidMap;
    private TreeMap<BigDecimal, PriorityQueue<Order>> askMap;
    private Timestamp updatedTime;


    public MarketData(String stockNo, BigDecimal bestBid, BigDecimal bestAsk, BigDecimal nominalPrice,
                      Timestamp updatedTime, TreeMap<BigDecimal, PriorityQueue<Order>> bidMap, TreeMap<BigDecimal, PriorityQueue<Order>> askMap) {
        this.stockNo = stockNo;
        this.bestBid = bestBid;
        this.bestAsk = bestAsk;
        this.nominalPrice = nominalPrice;
        this.updatedTime = updatedTime;
        this.bidMap = bidMap;
        this.askMap = askMap;
    }


    public String getStockNo() {
        return stockNo;
    }

    public void setStockNo(String stockNo) {
        this.stockNo = stockNo;
    }

    public BigDecimal getBestBid() {
        return bestBid;
    }

    public void setBestBid(BigDecimal bestBid) {
        this.bestBid = bestBid;
    }

    public BigDecimal getBestAsk() {
        return bestAsk;
    }

    public void setBestAsk(BigDecimal bestAsk) {
        this.bestAsk = bestAsk;
    }

    public BigDecimal getNominalPrice() {
        return nominalPrice;
    }

    public void setNominalPrice(BigDecimal nominalPrice) {
        this.nominalPrice = nominalPrice;
    }

    public TreeMap<BigDecimal, PriorityQueue<Order>> getBidMap() {
        return bidMap;
    }


    public void setBidMap(TreeMap<BigDecimal, PriorityQueue<Order>> bidMap) {
        this.bidMap = bidMap;
    }


    public TreeMap<BigDecimal, PriorityQueue<Order>> getAskMap() {
        return askMap;
    }


    public void setAskMap(TreeMap<BigDecimal, PriorityQueue<Order>> askMap) {
        this.askMap = askMap;
    }


    public Timestamp getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(Timestamp updatedTime) {
        this.updatedTime = updatedTime;
    }


}
