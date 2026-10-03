package pl.com.eltro.assortment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final PurchaseOrderReceiptService purchaseOrderReceiptService;

    public DeliveryService(
            DeliveryRepository deliveryRepository,
            PurchaseOrderReceiptService purchaseOrderReceiptService
    ) {
        this.deliveryRepository = deliveryRepository;
        this.purchaseOrderReceiptService = purchaseOrderReceiptService;
    }

    @Transactional
    public DeliveryRepository.DeliveryRow update(
            long deliveryId,
            UpdateDeliveryRequest request
    ) {
        if (request.quantity() <= 0) {
            throw new IllegalArgumentException(
                    "Delivery quantity must be greater than zero"
            );
        }

        BigDecimal purchase = request.purchasePriceNet();
        BigDecimal markup = request.markupPercent();

        if ((purchase == null) != (markup == null)) {
            throw new IllegalArgumentException(
                    "Provide both purchase price and markup, or leave both empty"
            );
        }

        BigDecimal salePrice = null;

        if (purchase != null) {
            if (purchase.signum() < 0 || purchase.scale() > 2
                    || markup.signum() < 0
                    || markup.compareTo(new BigDecimal("1000")) > 0
                    || markup.scale() > 2) {
                throw new IllegalArgumentException(
                        "Invalid purchase price or markup"
                );
            }

            salePrice = purchase
                    .multiply(
                            BigDecimal.ONE.add(markup.movePointLeft(2))
                    )
                    .setScale(2, RoundingMode.HALF_UP);
        }

        DeliveryRepository.DeliveryRow previous =
                deliveryRepository.findForUpdate(deliveryId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Delivery does not exist"
                        ));

        purchaseOrderReceiptService.lockProduct(previous.productId());

        long difference =
                (long) request.quantity() - previous.quantity();

        if (difference > Integer.MAX_VALUE
                || difference < Integer.MIN_VALUE) {
            throw new IllegalArgumentException(
                    "Quantity difference is too large"
            );
        }

        purchaseOrderReceiptService.validateStockChange(
                previous.productId(),
                difference
        );

        if (difference != 0 && deliveryRepository.changeProductQuantity(
                previous.productId(),
                (int) difference
        ) == 0) {
            throw new IllegalArgumentException(
                    "Cannot reduce delivery: insufficient current stock"
            );
        }

        deliveryRepository.saveCorrection(
                previous,
                request,
                salePrice
        );

        purchaseOrderReceiptService.correct(
                previous.productId(),
                deliveryId,
                previous.quantity(),
                request.quantity()
        );

        return new DeliveryRepository.DeliveryRow(
                previous.id(),
                previous.productId(),
                request.quantity(),
                purchase,
                markup,
                salePrice,
                request.remarks()
        );
    }
}