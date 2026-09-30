package pl.com.eltro.assortment;

import java.math.BigDecimal;

public record UpdateDeliveryRequest(
        int quantity,
        BigDecimal purchasePriceNet,
        BigDecimal markupPercent,
        String remarks
) {
}