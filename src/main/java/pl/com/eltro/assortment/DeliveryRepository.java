package pl.com.eltro.assortment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public class DeliveryRepository {

    private final JdbcTemplate jdbcTemplate;
    private final CurrentUserService currentUserService;

    public DeliveryRepository(
            JdbcTemplate jdbcTemplate,
            CurrentUserService currentUserService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.currentUserService = currentUserService;
    }

    public Optional<DeliveryRow> findForUpdate(long deliveryId) {
        return jdbcTemplate.query(
                """
                SELECT id, product_id, quantity_change,
                       unit_purchase_price_net, markup_percent,
                       unit_sale_price_net, remarks
                FROM stock_movements
                WHERE id = ?
                  AND movement_type = 'DELIVERY'
                FOR UPDATE
                """,
                (rs, rowNum) -> new DeliveryRow(
                        rs.getLong("id"),
                        rs.getLong("product_id"),
                        rs.getInt("quantity_change"),
                        rs.getBigDecimal("unit_purchase_price_net"),
                        rs.getBigDecimal("markup_percent"),
                        rs.getBigDecimal("unit_sale_price_net"),
                        rs.getString("remarks")
                ),
                deliveryId
        ).stream().findFirst();
    }

    public int changeProductQuantity(
            long productId,
            int quantityDifference
    ) {
        return jdbcTemplate.update(
                """
                UPDATE products
                SET quantity = quantity + ?
                WHERE id = ?
                  AND quantity + ? >= 0
                """,
                quantityDifference,
                productId,
                quantityDifference
        );
    }

    public void saveCorrection(
            DeliveryRow previous,
            UpdateDeliveryRequest request,
            BigDecimal newSalePriceNet
    ) {
        long correctedByUserId = currentUserService.requireUserId();

        jdbcTemplate.update(
                """
                INSERT INTO delivery_corrections (
                    delivery_movement_id,
                    previous_quantity, new_quantity,
                    previous_purchase_price_net, new_purchase_price_net,
                    previous_markup_percent, new_markup_percent,
                    previous_sale_price_net, new_sale_price_net,
                    previous_remarks, new_remarks,
                    corrected_by_user_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                previous.id(),
                previous.quantity(),
                request.quantity(),
                previous.purchasePriceNet(),
                request.purchasePriceNet(),
                previous.markupPercent(),
                request.markupPercent(),
                previous.salePriceNet(),
                newSalePriceNet,
                previous.remarks(),
                request.remarks(),
                correctedByUserId
        );

        jdbcTemplate.update(
                """
                UPDATE stock_movements
                SET quantity_change = ?,
                    unit_purchase_price_net = ?,
                    markup_percent = ?,
                    unit_sale_price_net = ?,
                    remarks = ?
                WHERE id = ?
                  AND movement_type = 'DELIVERY'
                """,
                request.quantity(),
                request.purchasePriceNet(),
                request.markupPercent(),
                newSalePriceNet,
                request.remarks(),
                previous.id()
        );
    }

    public record DeliveryRow(
            long id,
            long productId,
            int quantity,
            BigDecimal purchasePriceNet,
            BigDecimal markupPercent,
            BigDecimal salePriceNet,
            String remarks
    ) {
    }
}