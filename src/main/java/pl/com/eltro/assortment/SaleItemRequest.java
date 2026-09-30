package pl.com.eltro.assortment;

import java.math.BigDecimal;

public record SaleItemRequest(
        long productId,
        int quantity,
        BigDecimal unitSalePriceNet,
        BigDecimal discountPercent
) {
}
