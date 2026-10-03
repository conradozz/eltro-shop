package pl.com.eltro.assortment;

import java.time.OffsetDateTime;

public record PurchaseOrder(
        long id,
        long productId,
        String productName,
        String sku,
        String manufacturer,
        int quantity,
        int receivedQuantity,
        String status,
        String remarks,
        OffsetDateTime createdAt,
        OffsetDateTime orderedAt,
        String createdByUsername,
        int currentStock
) {
}