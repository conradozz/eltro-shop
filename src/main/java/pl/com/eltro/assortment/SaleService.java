package pl.com.eltro.assortment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class SaleService {

    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final ObjectMapper objectMapper;
    private final SalesDocumentRepository documentRepository;

    public SaleService(
            SaleRepository saleRepository,
            ProductRepository productRepository,
            CustomerRepository customerRepository,
            ObjectMapper objectMapper,
            SalesDocumentRepository documentRepository
    ) {
        this.saleRepository = saleRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.objectMapper = objectMapper;
        this.documentRepository = documentRepository;
    }

    @Transactional
    public long create(CreateSaleRequest request) {
        validateItems(request.items());

        BigDecimal customerDiscount =
                getCustomerDiscount(request.customerId());

        long saleId = saleRepository.createSale(
                request.customerId(),
                request.remarks()
        );

        saveItems(
                saleId,
                request.items(),
                customerDiscount
        );

        return saleId;
    }

    @Transactional
    public SaleDetails update(
            long saleId,
            CreateSaleRequest request
    ) {
        validateItems(request.items());

        if (!saleRepository.lockSale(saleId)) {
            throw new IllegalArgumentException(
                    "Sale does not exist"
            );
        }

        if (documentRepository.existsForSale(saleId)) {
            throw new IllegalArgumentException(
                    "Cannot edit a sale with an issued document"
            );
        }

        SaleDetails previous = saleRepository.findById(saleId)
                .orElseThrow();

        BigDecimal customerDiscount =
                getCustomerDiscount(request.customerId());

        for (SaleLine oldItem : previous.items()) {
            int changed = productRepository.changeQuantity(
                    oldItem.productId(),
                    oldItem.quantity()
            );

            if (changed == 0) {
                throw new IllegalArgumentException(
                        "Product from sale does not exist"
                );
            }

            saleRepository.createCorrectionMovement(
                    oldItem.productId(),
                    oldItem.quantity(),
                    saleId
            );
        }

        saleRepository.deactivateItems(saleId);

        saleRepository.updateHeader(
                saleId,
                request.customerId(),
                request.remarks()
        );

        saveItems(
                saleId,
                request.items(),
                customerDiscount
        );

        SaleDetails updated = saleRepository.findById(saleId)
                .orElseThrow();

        saleRepository.saveRevision(
                saleId,
                toJson(previous),
                toJson(updated)
        );

        return updated;
    }

    private void validateItems(List<SaleItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException(
                    "Sale must contain at least one item"
            );
        }

        for (SaleItemRequest item : items) {
            if (item == null) {
                throw new IllegalArgumentException(
                        "Sale item cannot be null"
                );
            }

            if (item.quantity() <= 0) {
                throw new IllegalArgumentException(
                        "Quantity must be positive"
                );
            }
        }
    }

    private BigDecimal getCustomerDiscount(Long customerId) {
        if (customerId == null) {
            return BigDecimal.ZERO;
        }

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Customer does not exist"
                        )
                );

        return customer.discountPercent();
    }

    private void saveItems(
            long saleId,
            List<SaleItemRequest> items,
            BigDecimal customerDiscount
    ) {
        for (SaleItemRequest item : items) {
            Product product = productRepository
                    .findById(item.productId())
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Product does not exist"
                            )
                    );

            BigDecimal price = item.unitSalePriceNet() == null
                    ? product.salePriceNet()
                    : item.unitSalePriceNet();

            BigDecimal discount = item.discountPercent() == null
                    ? customerDiscount
                    : item.discountPercent();

            if (price == null
                    || price.signum() < 0
                    || price.scale() > 2) {
                throw new IllegalArgumentException(
                        "Price must be non-negative "
                                + "with at most two decimal places"
                );
            }

            if (discount == null
                    || discount.signum() < 0
                    || discount.compareTo(new BigDecimal("100")) > 0
                    || discount.scale() > 2) {
                throw new IllegalArgumentException(
                        "Discount must be between 0 and 100 "
                                + "with at most two decimal places"
                );
            }

            int changed = productRepository.changeQuantity(
                    product.id(),
                    -item.quantity()
            );

            if (changed == 0) {
                throw new IllegalArgumentException(
                        "Insufficient stock for " + product.sku()
                );
            }

            long saleItemId = saleRepository.createSaleItem(
                    saleId,
                    product,
                    item.quantity(),
                    price,
                    discount
            );

            saleRepository.createSaleMovement(
                    product.id(),
                    saleItemId,
                    item.quantity()
            );
        }
    }

    private String toJson(SaleDetails sale) {
        try {
            return objectMapper.writeValueAsString(sale);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Cannot save sale revision",
                    exception
            );
        }
    }
}