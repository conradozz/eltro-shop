package pl.com.eltro.assortment;

public record PurchaseOrderRequest(
        Long productId,
        Integer quantity,
        String remarks
) {
}