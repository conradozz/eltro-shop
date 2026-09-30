package pl.com.eltro.assortment;

import java.math.BigDecimal;

public record UpdateProductDetailsRequest(
        String sku,
        String manufacturerPartNumber,
        String ean,
        String name,
        String manufacturer,
        String model,
        String category,
        BigDecimal vatRate,
        int minimumQuantity,
        String remarks
) {
}