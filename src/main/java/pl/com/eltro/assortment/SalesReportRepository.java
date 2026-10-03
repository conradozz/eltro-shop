package pl.com.eltro.assortment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

@Repository
public class SalesReportRepository {

    private static final ZoneId SHOP_ZONE =
            ZoneId.of("Europe/Warsaw");

    private final JdbcTemplate jdbc;

    public SalesReportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SalesReport.Sale> findSales(
            LocalDate from,
            LocalDate to,
            Long userId
    ) {
        LocalDateTime start = LocalDateTime.ofInstant(
                from.atStartOfDay(SHOP_ZONE).toInstant(),
                ZoneOffset.UTC
        );

        LocalDateTime end = LocalDateTime.ofInstant(
                to.plusDays(1).atStartOfDay(SHOP_ZONE).toInstant(),
                ZoneOffset.UTC
        );

        String sql = """
                SELECT s.id,
                       s.sold_at,
                       s.created_by_user_id,
                       u.username,
                       s.customer_id,
                       s.remarks,
                       COALESCE(SUM(i.quantity), 0) AS quantity,
                       COALESCE(SUM(i.line_net), 0) AS total_net,
                       COALESCE(
                           SUM(
                               ROUND(
                                   i.line_net * (1 + i.vat_rate / 100),
                                   2
                               )
                           ),
                           0
                       ) AS total_gross
                FROM sales s
                LEFT JOIN users u
                    ON u.id = s.created_by_user_id
                LEFT JOIN (
                    SELECT sale_id,
                           quantity,
                           vat_rate,
                           ROUND(
                               unit_sale_price_net * quantity
                                   * (1 - discount_percent / 100),
                               2
                           ) AS line_net
                    FROM sale_items
                    WHERE active = TRUE
                ) i ON i.sale_id = s.id
                WHERE s.sold_at >= ?
                  AND s.sold_at < ?
                """;

        if (userId != null) {
            sql += " AND s.created_by_user_id = ?";
        }

        sql += """
                 GROUP BY s.id,
                          s.sold_at,
                          s.created_by_user_id,
                          u.username,
                          s.customer_id,
                          s.remarks
                 ORDER BY s.sold_at DESC, s.id DESC
                """;

        Object[] parameters = userId == null
                ? new Object[]{start, end}
                : new Object[]{start, end, userId};

        return jdbc.query(
                sql,
                (rs, rowNum) -> new SalesReport.Sale(
                        rs.getLong("id"),
                        rs.getObject(
                                "sold_at",
                                LocalDateTime.class
                        ).atOffset(ZoneOffset.UTC),
                        rs.getObject("created_by_user_id", Long.class),
                        rs.getString("username"),
                        rs.getObject("customer_id", Long.class),
                        rs.getString("remarks"),
                        rs.getLong("quantity"),
                        rs.getBigDecimal("total_net").setScale(2),
                        rs.getBigDecimal("total_gross").setScale(2)
                ),
                parameters
        );
    }

    public List<SalesReport.User> findUsers() {
        return jdbc.query(
                "SELECT id, username FROM users ORDER BY username",
                (rs, rowNum) -> new SalesReport.User(
                        rs.getLong("id"),
                        rs.getString("username")
                )
        );
    }

    public boolean userExists(long id) {
        return !jdbc.query(
                "SELECT id FROM users WHERE id = ?",
                (rs, rowNum) -> rs.getLong("id"),
                id
        ).isEmpty();
    }
}