package pl.com.eltro.assortment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class PurchaseOrderRepository {

    private final JdbcTemplate jdbc;

    public PurchaseOrderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String SELECT = """
            SELECT po.*,
                   u.username AS author,
                   p.quantity AS current_stock
            FROM purchase_orders po
            JOIN products p ON p.id = po.product_id
            JOIN users u ON u.id = po.created_by_user_id
            """;

    private final RowMapper<PurchaseOrder> mapper = (rs, rowNum) -> {
        LocalDateTime orderedAt = rs.getObject(
                "ordered_at",
                LocalDateTime.class
        );

        return new PurchaseOrder(
                rs.getLong("id"),
                rs.getLong("product_id"),
                rs.getString("product_name"),
                rs.getString("sku"),
                rs.getString("manufacturer"),
                rs.getInt("quantity"),
                rs.getInt("received_quantity"),
                rs.getString("status"),
                rs.getString("remarks"),
                rs.getObject("created_at", LocalDateTime.class)
                        .atOffset(ZoneOffset.UTC),
                orderedAt == null
                        ? null
                        : orderedAt.atOffset(ZoneOffset.UTC),
                rs.getString("author"),
                rs.getInt("current_stock")
        );
    };

    public List<PurchaseOrder> findAll() {
        return jdbc.query(
                SELECT + """
                        ORDER BY COALESCE(po.manufacturer, ''),
                                 po.product_name,
                                 po.id DESC
                        """,
                mapper
        );
    }

    public Optional<PurchaseOrder> findById(long id) {
        return jdbc.query(
                SELECT + " WHERE po.id = ?",
                mapper,
                id
        ).stream().findFirst();
    }

    public boolean lock(long id) {
        return !jdbc.query(
                """
                SELECT id
                FROM purchase_orders
                WHERE id = ?
                FOR UPDATE
                """,
                (rs, rowNum) -> rs.getLong("id"),
                id
        ).isEmpty();
    }

    public long create(
            Product product,
            PurchaseOrderRequest request,
            long author
    ) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();

        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    """
                    INSERT INTO purchase_orders (
                        product_id,
                        product_name,
                        sku,
                        manufacturer,
                        quantity,
                        remarks,
                        created_by_user_id
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );

            statement.setLong(1, product.id());
            statement.setString(2, product.name());
            statement.setString(3, product.sku());
            statement.setString(4, product.manufacturer());
            statement.setInt(5, request.quantity());
            statement.setString(6, request.remarks());
            statement.setLong(7, author);

            return statement;
        }, keyHolder);

        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    public void updateDraft(
            long id,
            PurchaseOrderRequest request
    ) {
        jdbc.update(
                """
                UPDATE purchase_orders
                SET quantity = ?,
                    remarks = ?
                WHERE id = ?
                """,
                request.quantity(),
                request.remarks(),
                id
        );
    }

    public void changeStatus(long id, String status) {
        jdbc.update(
                """
                UPDATE purchase_orders
                SET status = ?,
                    ordered_at = CASE
                        WHEN ? = 'ORDERED' THEN CURRENT_TIMESTAMP
                        ELSE ordered_at
                    END
                WHERE id = ?
                """,
                status,
                status,
                id
        );
    }

    public void addEvent(
            long id,
            String type,
            PurchaseOrder previous,
            PurchaseOrder updated,
            long author
    ) {
        jdbc.update(
                """
                INSERT INTO purchase_order_events (
                    purchase_order_id,
                    event_type,
                    previous_quantity,
                    new_quantity,
                    previous_status,
                    new_status,
                    remarks,
                    created_by_user_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                type,
                previous == null ? null : previous.quantity(),
                updated.quantity(),
                previous == null ? null : previous.status(),
                updated.status(),
                updated.remarks(),
                author
        );
    }

    public List<PurchaseOrderEvent> findEvents(long id) {
        return jdbc.query(
                """
                SELECT e.*, u.username AS author
                FROM purchase_order_events e
                JOIN users u ON u.id = e.created_by_user_id
                WHERE e.purchase_order_id = ?
                ORDER BY e.id DESC
                """,
                (rs, rowNum) -> new PurchaseOrderEvent(
                        rs.getLong("id"),
                        rs.getString("event_type"),
                        rs.getObject("previous_quantity", Integer.class),
                        rs.getInt("new_quantity"),
                        rs.getString("previous_status"),
                        rs.getString("new_status"),
                        rs.getString("remarks"),
                        rs.getObject("created_at", LocalDateTime.class)
                                .atOffset(ZoneOffset.UTC),
                        rs.getString("author")
                ),
                id
        );
    }
}