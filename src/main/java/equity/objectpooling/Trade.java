package equity.objectpooling;

public record Trade(String buyBrokerID, String sellBrokerID, String buyOrderID, String sellOrderID,
					String stockNo, Double executedPrice, int executedQty, String executionDateTime) {

	@Override
	public String toString() {
		return (this.executionDateTime() + this.stockNo() + this.executedPrice() + this.executedQty());
	}
}