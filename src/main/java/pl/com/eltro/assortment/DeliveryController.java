package pl.com.eltro.assortment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/deliveries")
public class DeliveryController {

    private final JdbcTemplate jdbcTemplate;
    private final DeliveryService deliveryService;

    public DeliveryController(
            JdbcTemplate jdbcTemplate,
            DeliveryService deliveryService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.deliveryService = deliveryService;
    }

    @GetMapping
    public List<DeliverySummary> getAll() {
        return jdbcTemplate.query(
                """
                SELECT sm.id,
                       sm.product_id,
                       p.sku,
                       p.name AS product_name,
                       sm.quantity_change,
                       sm.unit_purchase_price_net,
                       sm.markup_percent,
                       sm.unit_sale_price_net,
                       sm.created_at,
                       sm.remarks,
                       u.username AS created_by_username
                FROM stock_movements sm
                JOIN products p ON p.id = sm.product_id
                LEFT JOIN users u ON u.id = sm.created_by_user_id
                WHERE sm.movement_type = 'DELIVERY'
                ORDER BY sm.id DESC
                """,
                (rs, rowNum) -> new DeliverySummary(
                        rs.getLong("id"),
                        rs.getLong("product_id"),
                        rs.getString("sku"),
                        rs.getString("product_name"),
                        rs.getInt("quantity_change"),
                        rs.getBigDecimal("unit_purchase_price_net"),
                        rs.getBigDecimal("markup_percent"),
                        rs.getBigDecimal("unit_sale_price_net"),
                        rs.getObject("created_at", LocalDateTime.class),
                        rs.getString("remarks"),
                        rs.getString("created_by_username")
                )
        );
    }

    @PutMapping("/{id}")
    public DeliveryRepository.DeliveryRow update(
            @PathVariable long id,
            @RequestBody UpdateDeliveryRequest request
    ) {
        return deliveryService.update(id, request);
    }

    public record DeliverySummary(
            long id,
            long productId,
            String sku,
            String productName,
            int quantity,
            BigDecimal purchasePriceNet,
            BigDecimal markupPercent,
            BigDecimal salePriceNet,
            LocalDateTime createdAt,
            String remarks,
            String createdByUsername
    ) {
    }
}