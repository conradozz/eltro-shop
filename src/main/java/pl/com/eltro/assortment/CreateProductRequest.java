package pl.com.eltro.assortment;

import java.math.BigDecimal;

public record CreateProductRequest(
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
        int minimumQuantity,
        String remarks
) {
}