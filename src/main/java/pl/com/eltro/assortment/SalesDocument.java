package pl.com.eltro.assortment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record SalesDocument(
        long id,
        long saleId,
        String documentType,
        String documentNumber,
        LocalDate issuedOn,
        LocalDate saleOn,
        DocumentIssuerSettings issuer,
        Customer customer,
        SaleDetails sale,
        BigDecimal totalNet,
        BigDecimal totalGross,
        String paymentStatus,
        String paymentMethod,
        LocalDate paymentDueOn,
        boolean goodsIssued,
        String recipientEmail,
        String remarks,
        OffsetDateTime createdAt,
        String createdByUsername
) {
}