package pl.com.eltro.assortment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record SaleDetails(
        Long id,
        Long customerId,
        OffsetDateTime soldAt,
        String remarks,
        List<SaleLine> items,
        BigDecimal totalNet,
        BigDecimal totalGross,
        String createdByUsername
) {
}