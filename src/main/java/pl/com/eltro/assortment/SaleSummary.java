package pl.com.eltro.assortment;

import java.time.OffsetDateTime;

public record SaleSummary(
        Long id,
        Long customerId,
        OffsetDateTime soldAt,
        String remarks,
        String createdByUsername
) {
}