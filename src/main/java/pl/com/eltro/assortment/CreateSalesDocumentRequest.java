package pl.com.eltro.assortment;

import java.time.LocalDate;

public record CreateSalesDocumentRequest(
        String documentType,
        String paymentStatus,
        String paymentMethod,
        LocalDate paymentDueOn,
        Boolean goodsIssued,
        String recipientEmail,
        String remarks
) {
}