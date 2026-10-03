package pl.com.eltro.assortment;

import java.time.OffsetDateTime;

public record PurchaseOrderEvent(
        long id,
        String eventType,
        Integer previousQuantity,
        int newQuantity,
        String previousStatus,
        String newStatus,
        String remarks,
        OffsetDateTime createdAt,
        String createdByUsername
) {
}