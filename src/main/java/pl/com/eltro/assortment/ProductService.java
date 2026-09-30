package pl.com.eltro.assortment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ShopSettingsRepository shopSettingsRepository;

    public ProductService(
            ProductRepository productRepository,
            ShopSettingsRepository shopSettingsRepository
    ) {
        this.productRepository = productRepository;
        this.shopSettingsRepository = shopSettingsRepository;
    }

    public Product create(CreateProductRequest request) {
        if (request.sku() == null || request.sku().isBlank()) {
            throw new IllegalArgumentException("SKU is required");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }
        if (request.purchasePriceNet() == null
                || request.purchasePriceNet().signum() < 0) {
            throw new IllegalArgumentException(
                    "Purchase price must be at least zero"
            );
        }
        if (request.vatRate() == null
                || request.vatRate().signum() < 0
                || request.vatRate().compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException(
                    "VAT rate must be between 0 and 100"
            );
        }
        if (request.minimumQuantity() < 0) {
            throw new IllegalArgumentException(
                    "Minimum quantity cannot be negative"
            );
        }

        BigDecimal salePriceNet = request.salePriceNet();

        if (salePriceNet == null) {
            BigDecimal markupPercent =
                    shopSettingsRepository.getDefaultMarkupPercent();

            BigDecimal multiplier = BigDecimal.ONE.add(
                    markupPercent.movePointLeft(2)
            );

            salePriceNet = request.purchasePriceNet()
                    .multiply(multiplier)
                    .setScale(2, RoundingMode.HALF_UP);
        } else if (salePriceNet.signum() < 0) {
            throw new IllegalArgumentException(
                    "Sale price cannot be negative"
            );
        }

        long id = productRepository.create(request, salePriceNet);
        return productRepository.findById(id).orElseThrow();
    }

    @Transactional
    public Product changeStock(long productId, StockChangeRequest request) {
        if (productRepository.findById(productId).isEmpty()) {
            throw new IllegalArgumentException("Product does not exist");
        }

        int change = request.quantityChange();
        String type = request.movementType();

        if (change == 0) {
            throw new IllegalArgumentException(
                    "Quantity change cannot be zero"
            );
        }
        if (type == null || !Set.of(
                "OPENING", "DELIVERY", "SALE", "RETURN", "CORRECTION"
        ).contains(type)) {
            throw new IllegalArgumentException("Invalid movement type");
        }
        if (("OPENING".equals(type)
                || "DELIVERY".equals(type)
                || "RETURN".equals(type)) && change < 0) {
            throw new IllegalArgumentException(
                    "This movement must increase stock"
            );
        }
        if ("SALE".equals(type)) {
            throw new IllegalArgumentException(
                    "Use /api/sales to sell a product"
            );
        }
        if ("DELIVERY".equals(type)) {
            throw new IllegalArgumentException(
                    "Use /api/products/{id}/deliveries for deliveries"
            );
        }

        int updatedRows =
                productRepository.changeQuantity(productId, change);

        if (updatedRows == 0) {
            throw new IllegalArgumentException("Insufficient stock");
        }

        productRepository.addStockMovement(productId, request);
        return productRepository.findById(productId).orElseThrow();
    }

    public Product updatePrices(
            long productId,
            UpdateProductPriceRequest request
    ) {
        BigDecimal purchase = request.purchasePriceNet();
        BigDecimal sale = request.salePriceNet();

        if (purchase == null || sale == null
                || purchase.signum() < 0 || sale.signum() < 0
                || purchase.scale() > 2 || sale.scale() > 2) {
            throw new IllegalArgumentException(
                    "Prices must be non-negative amounts " +
                            "with at most two decimal places"
            );
        }

        if (productRepository.findById(productId).isEmpty()) {
            throw new IllegalArgumentException("Product does not exist");
        }

        productRepository.updatePrices(productId, purchase, sale);
        return productRepository.findById(productId).orElseThrow();
    }

    public Product updateDetails(
            long productId,
            UpdateProductDetailsRequest request
    ) {
        if (request.sku() == null || request.sku().isBlank()
                || request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException(
                    "SKU and name are required"
            );
        }

        if (request.vatRate() == null
                || request.vatRate().signum() < 0
                || request.vatRate().compareTo(new BigDecimal("100")) > 0
                || request.vatRate().scale() > 2
                || request.minimumQuantity() < 0) {
            throw new IllegalArgumentException(
                    "Invalid VAT rate or minimum quantity"
            );
        }

        if (productRepository.updateDetails(productId, request) == 0) {
            throw new IllegalArgumentException("Product does not exist");
        }

        return productRepository.findById(productId).orElseThrow();
    }

    @Transactional
    public Product receiveDelivery(
            long productId,
            ReceiveDeliveryRequest request
    ) {
        if (request.quantity() <= 0
                || request.purchasePriceNet() == null
                || request.purchasePriceNet().signum() < 0
                || request.purchasePriceNet().scale() > 2
                || request.markupPercent() == null
                || request.markupPercent().signum() < 0
                || request.markupPercent()
                .compareTo(new BigDecimal("1000")) > 0
                || request.markupPercent().scale() > 2) {
            throw new IllegalArgumentException(
                    "Quantity, purchase price or markup is invalid"
            );
        }

        if (productRepository.findById(productId).isEmpty()) {
            throw new IllegalArgumentException("Product does not exist");
        }

        BigDecimal multiplier = BigDecimal.ONE.add(
                request.markupPercent().movePointLeft(2)
        );

        BigDecimal salePriceNet = request.purchasePriceNet()
                .multiply(multiplier)
                .setScale(2, RoundingMode.HALF_UP);

        productRepository.receiveDelivery(
                productId,
                request,
                salePriceNet
        );

        return productRepository.findById(productId).orElseThrow();
    }
}