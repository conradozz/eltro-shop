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
public class CustomerRepository {

    private final JdbcTemplate jdbcTemplate;

    public CustomerRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Customer> customerMapper = (rs, rowNum) ->
            new Customer(
                    rs.getLong("id"),
                    rs.getString("first_name"),
                    rs.getString("last_name"),
                    rs.getString("company_name"),
                    rs.getString("customer_type"),
                    rs.getString("nip"),
                    rs.getString("regon"),
                    rs.getString("street"),
                    rs.getString("postal_code"),
                    rs.getString("city"),
                    rs.getString("phone"),
                    rs.getString("email"),
                    rs.getBigDecimal("discount_percent"),
                    rs.getString("remarks")
            );

    public List<Customer> findAll() {
        return jdbcTemplate.query(
                "SELECT * FROM customers ORDER BY id",
                customerMapper
        );
    }

    public Optional<Customer> findById(long id) {
        return jdbcTemplate.query(
                "SELECT * FROM customers WHERE id = ?",
                customerMapper,
                id
        ).stream().findFirst();
    }

    public long create(
            CreateCustomerRequest request,
            BigDecimal discountPercent
    ) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO customers (
                        first_name, last_name, company_name, customer_type,
                        nip, regon, street, postal_code, city,
                        phone, email, discount_percent, remarks
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );

            statement.setString(1, request.firstName());
            statement.setString(2, request.lastName());
            statement.setString(3, request.companyName());
            statement.setString(4, request.customerType());
            statement.setString(5, request.nip());
            statement.setString(6, request.regon());
            statement.setString(7, request.street());
            statement.setString(8, request.postalCode());
            statement.setString(9, request.city());
            statement.setString(10, request.phone());
            statement.setString(11, request.email());
            statement.setBigDecimal(12, discountPercent);
            statement.setString(13, request.remarks());

            return statement;
        }, keyHolder);

        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    public List<Customer> search(String text) {
        String pattern = "%" + text.trim() + "%";

        return jdbcTemplate.query(
                """
                SELECT *
                FROM customers
                WHERE company_name LIKE ?
                   OR first_name LIKE ?
                   OR last_name LIKE ?
                   OR nip LIKE ?
                   OR city LIKE ?
                ORDER BY id
                """,
                customerMapper,
                pattern, pattern, pattern, pattern, pattern
        );
    }

    public int updateDiscount(
            long customerId,
            BigDecimal discountPercent
    ) {
        return jdbcTemplate.update(
                """
                UPDATE customers
                SET discount_percent = ?
                WHERE id = ?
                """,
                discountPercent,
                customerId
        );
    }

    public int update(
            long customerId,
            CreateCustomerRequest request,
            BigDecimal discountPercent
    ) {
        return jdbcTemplate.update(
                """
                UPDATE customers
                SET first_name = ?,
                    last_name = ?,
                    company_name = ?,
                    customer_type = ?,
                    nip = ?,
                    regon = ?,
                    street = ?,
                    postal_code = ?,
                    city = ?,
                    phone = ?,
                    email = ?,
                    discount_percent = ?,
                    remarks = ?
                WHERE id = ?
                """,
                request.firstName(),
                request.lastName(),
                request.companyName(),
                request.customerType(),
                request.nip(),
                request.regon(),
                request.street(),
                request.postalCode(),
                request.city(),
                request.phone(),
                request.email(),
                discountPercent,
                request.remarks(),
                customerId
        );
    }
}