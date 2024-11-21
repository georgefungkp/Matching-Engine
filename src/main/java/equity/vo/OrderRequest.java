package equity.vo;

import java.math.BigDecimal;


public record OrderRequest (String stockNo, String brokerId, String orderType, String buyOrSell, BigDecimal price, int quantity){}


