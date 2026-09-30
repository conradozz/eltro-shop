package pl.com.eltro.assortment;

import java.math.BigDecimal;

public record UpdateProductPriceRequest(
        BigDecimal purchasePriceNet,
        BigDecimal salePriceNet
) {
}