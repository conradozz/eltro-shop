package pl.com.eltro.assortment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SaleAvailabilityService {

    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;

    public SaleAvailabilityService(
            ProductRepository productRepository,
            CustomerRepository customerRepository
    ) {
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
    }

    @Transactional(readOnly = true)
    public Availability check(CreateSaleRequest request) {
        if (request == null
                || request.items() == null
                || request.items().isEmpty()) {
            throw new IllegalArgumentException(
                    "Sale must contain at least one item"
            );
        }

        BigDecimal customerDiscount = getCustomerDiscount(
                request.customerId()
        );

        Map<Long, Product> products = new HashMap<>();
        Map<Long, Integer> remainingStock = new HashMap<>();

        List<AvailabilityLine> lines = new ArrayList<>();
        List<SaleItemRequest> availableItems = new ArrayList<>();

        boolean hasShortages = false;

        for (SaleItemRequest item : request.items()) {
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

            Product product = products.computeIfAbsent(
                    item.productId(),
                    id -> productRepository.findById(id)
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "Product does not exist"
                                    )
                            )
            );

            BigDecimal price = item.unitSalePriceNet() == null
                    ? product.salePriceNet()
                    : item.unitSalePriceNet();

            BigDecimal discount = item.discountPercent() == null
                    ? customerDiscount
                    : item.discountPercent();

            validatePrice(price);
            validateDiscount(discount);

            int remaining = remainingStock.getOrDefault(
                    product.id(),
                    product.quantity()
            );

            int availableQuantity = Math.min(
                    item.quantity(),
                    remaining
            );

            int missingQuantity =
                    item.quantity() - availableQuantity;

            remainingStock.put(
                    product.id(),
                    remaining - availableQuantity
            );

            if (missingQuantity > 0) {
                hasShortages = true;
            }

            lines.add(new AvailabilityLine(
                    product.id(),
                    product.sku(),
                    product.name(),
                    item.quantity(),
                    product.quantity(),
                    availableQuantity,
                    missingQuantity,
                    price,
                    discount
            ));

            if (availableQuantity > 0) {
                availableItems.add(new SaleItemRequest(
                        product.id(),
                        availableQuantity,
                        price,
                        discount
                ));
            }
        }

        CreateSaleRequest availableRequest =
                new CreateSaleRequest(
                        request.customerId(),
                        List.copyOf(availableItems),
                        request.remarks()
                );

        return new Availability(
                hasShortages,
                !availableItems.isEmpty(),
                List.copyOf(lines),
                availableRequest
        );
    }

    private BigDecimal getCustomerDiscount(Long customerId) {
        if (customerId == null) {
            return BigDecimal.ZERO;
        }

        return customerRepository.findById(customerId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Customer does not exist"
                        )
                )
                .discountPercent();
    }

    private void validatePrice(BigDecimal price) {
        if (price == null
                || price.signum() < 0
                || price.scale() > 2) {
            throw new IllegalArgumentException(
                    "Price must be non-negative "
                            + "with at most two decimal places"
            );
        }
    }

    private void validateDiscount(BigDecimal discount) {
        if (discount == null
                || discount.signum() < 0
                || discount.compareTo(new BigDecimal("100")) > 0
                || discount.scale() > 2) {
            throw new IllegalArgumentException(
                    "Discount must be between 0 and 100 "
                            + "with at most two decimal places"
            );
        }
    }

    public record Availability(
            boolean hasShortages,
            boolean canSellAnything,
            List<AvailabilityLine> lines,
            CreateSaleRequest availableRequest
    ) {
    }

    public record AvailabilityLine(
            long productId,
            String sku,
            String productName,
            int requestedQuantity,
            int stockQuantity,
            int availableQuantity,
            int missingQuantity,
            BigDecimal unitSalePriceNet,
            BigDecimal discountPercent
    ) {
    }
}