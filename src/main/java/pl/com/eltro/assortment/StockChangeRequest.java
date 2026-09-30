package pl.com.eltro.assortment;

public record StockChangeRequest(
        int quantityChange,
        String movementType,
        String remarks
) {
}