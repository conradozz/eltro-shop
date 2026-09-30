package pl.com.eltro.assortment;

import java.math.BigDecimal;

public record UpdateDiscountRequest(
        BigDecimal discountPercent
) {
}