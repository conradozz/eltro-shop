package pl.com.eltro.assortment;

import java.math.BigDecimal;

public record SaleLine(
        Long id,
        Long productId,
        String sku,
        String productName,
        int quantity,
        BigDecimal unitSalePriceNet,
        BigDecimal discountPercent,
        BigDecimal vatRate,
        BigDecimal lineTotalNet,
        BigDecimal lineTotalGross
) {
}
