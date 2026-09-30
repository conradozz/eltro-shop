package pl.com.eltro.assortment;

import java.math.BigDecimal;

public record ReceiveDeliveryRequest(
        int quantity,
        BigDecimal purchasePriceNet,
        BigDecimal markupPercent,
        String remarks
) {
}