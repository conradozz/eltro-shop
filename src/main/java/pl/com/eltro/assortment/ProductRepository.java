package pl.com.eltro.assortment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class ProductRepository {

    private final JdbcTemplate jdbcTemplate;
    private final CurrentUserService currentUserService;

    public ProductRepository(
            JdbcTemplate jdbcTemplate,
            CurrentUserService currentUserService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.currentUserService = currentUserService;
    }

    private final RowMapper<Product> productMapper = (rs, rowNum) ->
            new Product(
                    rs.getLong("id"),
                    rs.getString("sku"),
                    rs.getString("manufacturer_part_number"),
                    rs.getString("ean"),
                    rs.getString("name"),
                    rs.getString("manufacturer"),
                    rs.getString("model"),
                    rs.getString("category"),
                    rs.getBigDecimal("purchase_price_net"),
                    rs.getBigDecimal("sale_price_net"),
                    rs.getBigDecimal("vat_rate"),
                    rs.getInt("quantity"),
                    rs.getInt("minimum_quantity"),
                    rs.getString("remarks")
            );

    public List<Product> findAll() {
        return jdbcTemplate.query(
                "SELECT * FROM products ORDER BY name",
                productMapper
        );
    }

    public Optional<Product> findById(long id) {
        return jdbcTemplate.query(
                "SELECT * FROM products WHERE id = ?",
                productMapper,
                id
        ).stream().findFirst();
    }

    public long create(
            CreateProductRequest request,
            BigDecimal salePriceNet
    ) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO products (
                        sku, manufacturer_part_number, ean, name,
                        manufacturer, model, category,
                        purchase_price_net, sale_price_net, vat_rate,
                        minimum_quantity, remarks
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );

            statement.setString(1, request.sku());
            statement.setString(2, request.manufacturerPartNumber());
            statement.setString(3, request.ean());
            statement.setString(4, request.name());
            statement.setString(5, request.manufacturer());
            statement.setString(6, request.model());
            statement.setString(7, request.category());
            statement.setBigDecimal(8, request.purchasePriceNet());
            statement.setBigDecimal(9, salePriceNet);
            statement.setBigDecimal(10, request.vatRate());
            statement.setInt(11, request.minimumQuantity());
            statement.setString(12, request.remarks());

            return statement;
        }, keyHolder);

        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    public int changeQuantity(long productId, int quantityChange) {
        return jdbcTemplate.update(
                """
                UPDATE products
                SET quantity = quantity + ?
                WHERE id = ? AND quantity + ? >= 0
                """,
                quantityChange,
                productId,
                quantityChange
        );
    }

    public void addStockMovement(
            long productId,
            StockChangeRequest request
    ) {
        long userId = currentUserService.requireUserId();

        jdbcTemplate.update(
                """
                INSERT INTO stock_movements (
                    product_id, quantity_change, movement_type,
                    remarks, created_by_user_id
                )
                VALUES (?, ?, ?, ?, ?)
                """,
                productId,
                request.quantityChange(),
                request.movementType(),
                request.remarks(),
                userId
        );
    }

    public List<Product> search(String text) {
        String pattern = "%" + text.trim() + "%";

        return jdbcTemplate.query(
                """
                SELECT *
                FROM products
                WHERE sku LIKE ?
                   OR name LIKE ?
                   OR manufacturer LIKE ?
                   OR model LIKE ?
                   OR category LIKE ?
                ORDER BY name
                """,
                productMapper,
                pattern,
                pattern,
                pattern,
                pattern,
                pattern
        );
    }

    public int updatePrices(
            long productId,
            BigDecimal purchasePriceNet,
            BigDecimal salePriceNet
    ) {
        return jdbcTemplate.update(
                """
                UPDATE products
                SET purchase_price_net = ?,
                    sale_price_net = ?
                WHERE id = ?
                """,
                purchasePriceNet,
                salePriceNet,
                productId
        );
    }

    public int updateDetails(
            long id,
            UpdateProductDetailsRequest request
    ) {
        return jdbcTemplate.update(
                """
                UPDATE products
                SET sku = ?,
                    manufacturer_part_number = ?,
                    ean = ?,
                    name = ?,
                    manufacturer = ?,
                    model = ?,
                    category = ?,
                    vat_rate = ?,
                    minimum_quantity = ?,
                    remarks = ?
                WHERE id = ?
                """,
                request.sku().trim(),
                request.manufacturerPartNumber(),
                request.ean(),
                request.name().trim(),
                request.manufacturer(),
                request.model(),
                request.category(),
                request.vatRate(),
                request.minimumQuantity(),
                request.remarks(),
                id
        );
    }

    public void receiveDelivery(
            long id,
            ReceiveDeliveryRequest request,
            BigDecimal salePriceNet
    ) {
        long userId = currentUserService.requireUserId();

        jdbcTemplate.update(
                """
                UPDATE products
                SET quantity = quantity + ?,
                    purchase_price_net = ?,
                    sale_price_net = ?
                WHERE id = ?
                """,
                request.quantity(),
                request.purchasePriceNet(),
                salePriceNet,
                id
        );

        jdbcTemplate.update(
                """
                INSERT INTO stock_movements (
                    product_id, quantity_change, movement_type, remarks,
                    unit_purchase_price_net, markup_percent,
                    unit_sale_price_net, created_by_user_id
                )
                VALUES (?, ?, 'DELIVERY', ?, ?, ?, ?, ?)
                """,
                id,
                request.quantity(),
                request.remarks(),
                request.purchasePriceNet(),
                request.markupPercent(),
                salePriceNet,
                userId
        );
    }
}