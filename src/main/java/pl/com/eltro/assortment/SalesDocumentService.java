package pl.com.eltro.assortment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class SalesDocumentService {

    private static final ZoneId SHOP_ZONE =
            ZoneId.of("Europe/Warsaw");

    private final SalesDocumentRepository documentRepository;
    private final SaleRepository saleRepository;
    private final CustomerRepository customerRepository;
    private final DocumentIssuerRepository issuerRepository;
    private final CurrentUserService currentUserService;

    public SalesDocumentService(
            SalesDocumentRepository documentRepository,
            SaleRepository saleRepository,
            CustomerRepository customerRepository,
            DocumentIssuerRepository issuerRepository,
            CurrentUserService currentUserService
    ) {
        this.documentRepository = documentRepository;
        this.saleRepository = saleRepository;
        this.customerRepository = customerRepository;
        this.issuerRepository = issuerRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public SalesDocument create(
            long saleId,
            CreateSalesDocumentRequest request
    ) {
        long authorId = currentUserService.requireUserId();

        if (request == null) {
            throw new IllegalArgumentException(
                    "Document data is required"
            );
        }

        validateChoice(
                request.documentType(),
                Set.of("INVOICE", "SALE_CONFIRMATION"),
                "Invalid document type"
        );

        validateChoice(
                request.paymentStatus(),
                Set.of("PAID", "DEFERRED", "UNPAID"),
                "Invalid payment status"
        );

        validateChoice(
                request.paymentMethod(),
                Set.of("CASH", "CARD", "TRANSFER", "OTHER"),
                "Invalid payment method"
        );

        if (request.goodsIssued() == null) {
            throw new IllegalArgumentException(
                    "Specify whether goods have been issued"
            );
        }

        // Ta sama blokada jest używana przy korekcie sprzedaży.
        if (!saleRepository.lockSale(saleId)) {
            throw new IllegalArgumentException(
                    "Sale does not exist"
            );
        }

        if (documentRepository.existsForSaleAndType(
                saleId,
                request.documentType()
        )) {
            throw new IllegalArgumentException(
                    "This document type has already been "
                            + "issued for this sale"
            );
        }

        SaleDetails sale = saleRepository.findById(saleId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Sale does not exist"
                        )
                );

        if (sale.items().isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot issue a document for an empty sale"
            );
        }

        DocumentIssuerSettings issuer =
                issuerRepository.getSettings();

        requireText(
                issuer.companyName(),
                "Configure document issuer company name"
        );

        Customer customer = null;

        if (sale.customerId() != null) {
            customer = customerRepository
                    .findById(sale.customerId())
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Customer does not exist"
                            )
                    );
        }

        if ("INVOICE".equals(request.documentType())) {
            validateInvoiceData(issuer, customer);
        }

        LocalDate issuedOn = LocalDate.now(SHOP_ZONE);

        LocalDate saleOn = sale.soldAt()
                .atZoneSameInstant(SHOP_ZONE)
                .toLocalDate();

        LocalDate paymentDueOn = null;

        if ("DEFERRED".equals(request.paymentStatus())) {
            paymentDueOn = request.paymentDueOn();

            if (paymentDueOn == null
                    || paymentDueOn.isBefore(issuedOn)) {
                throw new IllegalArgumentException(
                        "Deferred payment requires a due date "
                                + "not earlier than the issue date"
                );
            }
        }

        String recipientEmail = normalizeEmail(
                request.recipientEmail()
        );

        if (recipientEmail == null && customer != null) {
            recipientEmail = normalizeEmail(customer.email());
        }

        String remarks = normalizeText(request.remarks());

        if (remarks != null && remarks.length() > 2000) {
            throw new IllegalArgumentException(
                    "Document remarks cannot exceed 2000 characters"
            );
        }

        CreateSalesDocumentRequest normalized =
                new CreateSalesDocumentRequest(
                        request.documentType(),
                        request.paymentStatus(),
                        request.paymentMethod(),
                        paymentDueOn,
                        request.goodsIssued(),
                        recipientEmail,
                        remarks
                );

        String number = documentRepository.nextNumber(
                normalized.documentType(),
                issuedOn.getYear()
        );

        long documentId = documentRepository.create(
                sale,
                issuer,
                customer,
                normalized,
                number,
                issuedOn,
                saleOn,
                authorId
        );

        return documentRepository.findById(documentId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Created document could not be read"
                        )
                );
    }

    @Transactional(readOnly = true)
    public Optional<SalesDocument> findById(long id) {
        return documentRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<SalesDocument> findBySaleId(long saleId) {
        return documentRepository.findBySaleId(saleId);
    }

    private void validateInvoiceData(
            DocumentIssuerSettings issuer,
            Customer customer
    ) {
        requireNip(
                issuer.nip(),
                "Configure issuer NIP with 10 digits"
        );
        requireText(
                issuer.street(),
                "Configure issuer street"
        );
        requireText(
                issuer.postalCode(),
                "Configure issuer postal code"
        );
        requireText(
                issuer.city(),
                "Configure issuer city"
        );

        if (customer == null) {
            throw new IllegalArgumentException(
                    "Assign a customer to the sale "
                            + "before issuing an invoice"
            );
        }

        if ("COMPANY".equals(customer.customerType())) {
            requireText(
                    customer.companyName(),
                    "Customer company name is required"
            );
            requireNip(
                    customer.nip(),
                    "Customer NIP must contain 10 digits"
            );
        } else if ("PERSON".equals(customer.customerType())) {
            requireText(
                    customer.firstName(),
                    "Customer first name is required"
            );
            requireText(
                    customer.lastName(),
                    "Customer last name is required"
            );
        } else {
            throw new IllegalArgumentException(
                    "Invalid customer type"
            );
        }

        requireText(
                customer.street(),
                "Customer street is required"
        );
        requireText(
                customer.postalCode(),
                "Customer postal code is required"
        );
        requireText(
                customer.city(),
                "Customer city is required"
        );
    }

    private void validateChoice(
            String value,
            Set<String> allowed,
            String error
    ) {
        if (value == null || !allowed.contains(value)) {
            throw new IllegalArgumentException(error);
        }
    }

    private void requireText(String value, String error) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(error);
        }
    }

    private void requireNip(String value, String error) {
        if (value == null || !value.matches("[0-9]{10}")) {
            throw new IllegalArgumentException(error);
        }
    }

    private String normalizeText(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }

    private String normalizeEmail(String value) {
        String email = normalizeText(value);

        if (email != null
                && (email.length() > 200
                || !email.matches(
                "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"
        ))) {
            throw new IllegalArgumentException(
                    "Invalid recipient email address"
            );
        }

        return email;
    }
}