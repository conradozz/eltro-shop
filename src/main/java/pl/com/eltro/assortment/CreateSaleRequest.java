package pl.com.eltro.assortment;

import java.util.List;

public record CreateSaleRequest(
        Long customerId,
        List<SaleItemRequest> items,
        String remarks
) {
}
