package pl.com.eltro.assortment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/admin/deliveries")
public class DeliveryCorrectionController {

    private final JdbcTemplate jdbcTemplate;

    public DeliveryCorrectionController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/{id}/corrections")
    public List<DeliveryCorrection> getCorrections(
            @PathVariable long id
    ) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM stock_movements
                WHERE id = ?
                  AND movement_type = 'DELIVERY'
                """,
                Integer.class,
                id
        );

        if (count == null || count == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Delivery does not exist"
            );
        }

        return jdbcTemplate.query(
                """
                SELECT dc.id,
                       dc.delivery_movement_id,
                       dc.previous_quantity,
                       dc.new_quantity,
                       dc.previous_purchase_price_net,
                       dc.new_purchase_price_net,
                       dc.previous_markup_percent,
                       dc.new_markup_percent,
                       dc.previous_sale_price_net,
                       dc.new_sale_price_net,
                       dc.previous_remarks,
                       dc.new_remarks,
                       u.username AS corrected_by_username
                FROM delivery_corrections dc
                LEFT JOIN users u ON u.id = dc.corrected_by_user_id
                WHERE dc.delivery_movement_id = ?
                ORDER BY dc.id DESC
                """,
                (rs, rowNum) -> new DeliveryCorrection(
                        rs.getLong("id"),
                        rs.getLong("delivery_movement_id"),
                        rs.getInt("previous_quantity"),
                        rs.getInt("new_quantity"),
                        rs.getBigDecimal("previous_purchase_price_net"),
                        rs.getBigDecimal("new_purchase_price_net"),
                        rs.getBigDecimal("previous_markup_percent"),
                        rs.getBigDecimal("new_markup_percent"),
                        rs.getBigDecimal("previous_sale_price_net"),
                        rs.getBigDecimal("new_sale_price_net"),
                        rs.getString("previous_remarks"),
                        rs.getString("new_remarks"),
                        rs.getString("corrected_by_username")
                ),
                id
        );
    }

    public record DeliveryCorrection(
            long id,
            long deliveryId,
            int previousQuantity,
            int newQuantity,
            BigDecimal previousPurchasePriceNet,
            BigDecimal newPurchasePriceNet,
            BigDecimal previousMarkupPercent,
            BigDecimal newMarkupPercent,
            BigDecimal previousSalePriceNet,
            BigDecimal newSalePriceNet,
            String previousRemarks,
            String newRemarks,
            String correctedByUsername
    ) {
    }
}