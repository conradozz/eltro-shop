package pl.com.eltro.assortment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class SaleRepository {

    private final JdbcTemplate jdbcTemplate;
    private final CurrentUserService currentUserService;

    public SaleRepository(
            JdbcTemplate jdbcTemplate,
            CurrentUserService currentUserService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.currentUserService = currentUserService;
    }

    public long createSale(Long customerId, String remarks) {
        long userId = currentUserService.requireUserId();
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO sales (
                        customer_id, remarks, created_by_user_id
                    )
                    VALUES (?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );

            if (customerId == null) {
                statement.setNull(1, Types.BIGINT);
            } else {
                statement.setLong(1, customerId);
            }

            statement.setString(2, remarks);
            statement.setLong(3, userId);

            return statement;
        }, keyHolder);

        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    public long createSaleItem(
            long saleId,
            Product product,
            int quantity,
            BigDecimal unitSalePriceNet,
            BigDecimal discountPercent
    ) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO sale_items (
                        sale_id, product_id, product_name, quantity,
                        unit_purchase_price_net, unit_sale_price_net,
                        discount_percent, vat_rate
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );

            statement.setLong(1, saleId);
            statement.setLong(2, product.id());
            statement.setString(3, product.name());
            statement.setInt(4, quantity);
            statement.setBigDecimal(5, product.purchasePriceNet());
            statement.setBigDecimal(6, unitSalePriceNet);
            statement.setBigDecimal(7, discountPercent);
            statement.setBigDecimal(8, product.vatRate());

            return statement;
        }, keyHolder);

        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    public void createSaleMovement(
            long productId,
            long saleItemId,
            int quantity
    ) {
        long userId = currentUserService.requireUserId();

        jdbcTemplate.update(
                """
                INSERT INTO stock_movements (
                    product_id, quantity_change, movement_type,
                    sale_item_id, remarks, created_by_user_id
                )
                VALUES (?, ?, 'SALE', ?, ?, ?)
                """,
                productId,
                -quantity,
                saleItemId,
                "Sale item " + saleItemId,
                userId
        );
    }

    public boolean lockSale(long saleId) {
        return !jdbcTemplate.query(
                "SELECT id FROM sales WHERE id = ? FOR UPDATE",
                (rs, rowNum) -> rs.getLong("id"),
                saleId
        ).isEmpty();
    }

    public void updateHeader(
            long saleId,
            Long customerId,
            String remarks
    ) {
        jdbcTemplate.update(
                """
                UPDATE sales
                SET customer_id = ?, remarks = ?
                WHERE id = ?
                """,
                customerId,
                remarks,
                saleId
        );
    }

    public void deactivateItems(long saleId) {
        jdbcTemplate.update(
                """
                UPDATE sale_items
                SET active = FALSE
                WHERE sale_id = ? AND active = TRUE
                """,
                saleId
        );
    }

    public void createCorrectionMovement(
            long productId,
            int quantity,
            long saleId
    ) {
        long userId = currentUserService.requireUserId();

        jdbcTemplate.update(
                """
                INSERT INTO stock_movements (
                    product_id, quantity_change, movement_type,
                    remarks, created_by_user_id
                )
                VALUES (?, ?, 'CORRECTION', ?, ?)
                """,
                productId,
                quantity,
                "Correction of sale " + saleId,
                userId
        );
    }

    public void saveRevision(
            long saleId,
            String previousSnapshot,
            String newSnapshot
    ) {
        long userId = currentUserService.requireUserId();

        jdbcTemplate.update(
                """
                INSERT INTO sale_revisions (
                    sale_id, previous_snapshot, new_snapshot,
                    corrected_by_user_id
                )
                VALUES (?, ?, ?, ?)
                """,
                saleId,
                previousSnapshot,
                newSnapshot,
                userId
        );
    }

    public Optional<SaleDetails> findById(long saleId) {
        List<SaleDetails> headers = jdbcTemplate.query(
                """
                SELECT s.id,
                       s.customer_id,
                       s.sold_at,
                       s.remarks,
                       u.username AS created_by_username
                FROM sales s
                LEFT JOIN users u ON u.id = s.created_by_user_id
                WHERE s.id = ?
                """,
                (rs, rowNum) -> new SaleDetails(
                        rs.getLong("id"),
                        rs.getObject("customer_id", Long.class),
                        rs.getObject("sold_at", LocalDateTime.class)
                                .atOffset(ZoneOffset.UTC),
                        rs.getString("remarks"),
                        List.of(),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        rs.getString("created_by_username")
                ),
                saleId
        );

        if (headers.isEmpty()) {
            return Optional.empty();
        }

        List<SaleLine> items = jdbcTemplate.query(
                """
                SELECT si.*, p.sku
                FROM sale_items si
                JOIN products p ON p.id = si.product_id
                WHERE si.sale_id = ?
                  AND si.active = TRUE
                ORDER BY si.id
                """,
                (rs, rowNum) -> {
                    BigDecimal price =
                            rs.getBigDecimal("unit_sale_price_net");
                    BigDecimal discount =
                            rs.getBigDecimal("discount_percent");
                    int quantity = rs.getInt("quantity");

                    BigDecimal lineTotal = price
                            .multiply(BigDecimal.valueOf(quantity))
                            .multiply(
                                    BigDecimal.ONE.subtract(
                                            discount.movePointLeft(2)
                                    )
                            )
                            .setScale(2, RoundingMode.HALF_UP);

                    BigDecimal vatRate = rs.getBigDecimal("vat_rate");

                    BigDecimal lineTotalGross = lineTotal
                            .multiply(
                                    BigDecimal.ONE.add(
                                            vatRate.movePointLeft(2)
                                    )
                            )
                            .setScale(2, RoundingMode.HALF_UP);

                    return new SaleLine(
                            rs.getLong("id"),
                            rs.getLong("product_id"),
                            rs.getString("sku"),
                            rs.getString("product_name"),
                            quantity,
                            price,
                            discount,
                            vatRate,
                            lineTotal,
                            lineTotalGross
                    );
                },
                saleId
        );

        BigDecimal totalNet = items.stream()
                .map(SaleLine::lineTotalNet)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalGross = items.stream()
                .map(SaleLine::lineTotalGross)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        SaleDetails header = headers.get(0);

        return Optional.of(new SaleDetails(
                header.id(),
                header.customerId(),
                header.soldAt(),
                header.remarks(),
                items,
                totalNet,
                totalGross,
                header.createdByUsername()
        ));
    }

    public List<SaleSummary> findRecent() {
        return jdbcTemplate.query(
                """
                SELECT s.id,
                       s.customer_id,
                       s.sold_at,
                       s.remarks,
                       u.username AS created_by_username
                FROM sales s
                LEFT JOIN users u ON u.id = s.created_by_user_id
                ORDER BY s.sold_at DESC, s.id DESC
                LIMIT 100
                """,
                (rs, rowNum) -> new SaleSummary(
                        rs.getLong("id"),
                        rs.getObject("customer_id", Long.class),
                        rs.getObject("sold_at", LocalDateTime.class)
                                .atOffset(ZoneOffset.UTC),
                        rs.getString("remarks"),
                        rs.getString("created_by_username")
                )
        );
    }
}