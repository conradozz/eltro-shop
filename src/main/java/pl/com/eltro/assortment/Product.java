package pl.com.eltro.assortment;

import java.math.BigDecimal;

import java.math.BigDecimal;

public record Product(
        Long id,
        String sku,
        String manufacturerPartNumber,
        String ean,
        String name,
        String manufacturer,
        String model,
        String category,
        BigDecimal purchasePriceNet,
        BigDecimal salePriceNet,
        BigDecimal vatRate,
        int quantity,
        int minimumQuantity,
        String remarks
) {
}
