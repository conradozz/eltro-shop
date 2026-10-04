package pl.com.eltro.assortment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Repository
public class SalesDocumentRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public SalesDocumentRepository(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String nextNumber(String documentType, int year) {
        String prefix = switch (documentType) {
            case "INVOICE" -> "FV";
            case "SALE_CONFIRMATION" -> "PS";
            default -> throw new IllegalArgumentException(
                    "Invalid document type"
            );
        };

        jdbcTemplate.update(
                """
                INSERT INTO document_sequences (
                    document_type, document_year, last_number
                )
                VALUES (?, ?, 0)
                ON DUPLICATE KEY UPDATE
                    last_number = last_number
                """,
                documentType,
                year
        );

        Long previousNumber = jdbcTemplate.queryForObject(
                """
                SELECT last_number
                FROM document_sequences
                WHERE document_type = ?
                  AND document_year = ?
                FOR UPDATE
                """,
                Long.class,
                documentType,
                year
        );

        long nextNumber = Math.addExact(
                Objects.requireNonNull(previousNumber),
                1L
        );

        jdbcTemplate.update(
                """
                UPDATE document_sequences
                SET last_number = ?
                WHERE document_type = ?
                  AND document_year = ?
                """,
                nextNumber,
                documentType,
                year
        );

        return String.format(
                Locale.ROOT,
                "%s/%d/%06d",
                prefix,
                year,
                nextNumber
        );
    }

    public boolean existsForSaleAndType(
            long saleId,
            String documentType
    ) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM sales_documents
                WHERE sale_id = ?
                  AND document_type = ?
                """,
                Integer.class,
                saleId,
                documentType
        );

        return count != null && count > 0;
    }

    public boolean existsForSale(long saleId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM sales_documents
                WHERE sale_id = ?
                """,
                Integer.class,
                saleId
        );

        return count != null && count > 0;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public long create(
            SaleDetails sale,
            DocumentIssuerSettings issuer,
            Customer customer,
            CreateSalesDocumentRequest request,
            String documentNumber,
            LocalDate issuedOn,
            LocalDate saleOn,
            long authorId
    ) {
        String issuerJson = toJson(issuer);
        String customerJson = customer == null
                ? null
                : toJson(customer);
        String saleJson = toJson(sale);

        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO sales_documents (
                        sale_id,
                        document_type,
                        document_number,
                        issued_on,
                        sale_on,
                        issuer_snapshot,
                        customer_snapshot,
                        sale_snapshot,
                        total_net,
                        total_gross,
                        payment_status,
                        payment_method,
                        payment_due_on,
                        goods_issued,
                        recipient_email,
                        remarks,
                        created_by_user_id
                    )
                    VALUES (
                        ?, ?, ?, ?, ?, ?, ?, ?, ?,
                        ?, ?, ?, ?, ?, ?, ?, ?
                    )
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );

            statement.setLong(1, sale.id());
            statement.setString(2, request.documentType());
            statement.setString(3, documentNumber);
            statement.setObject(4, issuedOn);
            statement.setObject(5, saleOn);
            statement.setString(6, issuerJson);

            if (customerJson == null) {
                statement.setNull(7, Types.VARCHAR);
            } else {
                statement.setString(7, customerJson);
            }

            statement.setString(8, saleJson);
            statement.setBigDecimal(9, sale.totalNet());
            statement.setBigDecimal(10, sale.totalGross());
            statement.setString(11, request.paymentStatus());
            statement.setString(12, request.paymentMethod());

            if (request.paymentDueOn() == null) {
                statement.setNull(13, Types.DATE);
            } else {
                statement.setObject(13, request.paymentDueOn());
            }

            statement.setBoolean(
                    14,
                    Boolean.TRUE.equals(request.goodsIssued())
            );
            statement.setString(15, request.recipientEmail());
            statement.setString(16, request.remarks());
            statement.setLong(17, authorId);

            return statement;
        }, keyHolder);

        return Objects.requireNonNull(
                keyHolder.getKey()
        ).longValue();
    }

    public Optional<SalesDocument> findById(long id) {
        return jdbcTemplate.query(
                """
                SELECT d.*,
                       u.username AS created_by_username
                FROM sales_documents d
                JOIN users u ON u.id = d.created_by_user_id
                WHERE d.id = ?
                """,
                documentMapper(),
                id
        ).stream().findFirst();
    }

    public List<SalesDocument> findBySaleId(long saleId) {
        return jdbcTemplate.query(
                """
                SELECT d.*,
                       u.username AS created_by_username
                FROM sales_documents d
                JOIN users u ON u.id = d.created_by_user_id
                WHERE d.sale_id = ?
                ORDER BY d.id DESC
                """,
                documentMapper(),
                saleId
        );
    }

    private RowMapper<SalesDocument> documentMapper() {
        return (rs, rowNum) -> new SalesDocument(
                rs.getLong("id"),
                rs.getLong("sale_id"),
                rs.getString("document_type"),
                rs.getString("document_number"),
                rs.getObject("issued_on", LocalDate.class),
                rs.getObject("sale_on", LocalDate.class),
                fromJson(
                        rs.getString("issuer_snapshot"),
                        DocumentIssuerSettings.class
                ),
                fromJson(
                        rs.getString("customer_snapshot"),
                        Customer.class
                ),
                fromJson(
                        rs.getString("sale_snapshot"),
                        SaleDetails.class
                ),
                rs.getBigDecimal("total_net"),
                rs.getBigDecimal("total_gross"),
                rs.getString("payment_status"),
                rs.getString("payment_method"),
                rs.getObject("payment_due_on", LocalDate.class),
                rs.getBoolean("goods_issued"),
                rs.getString("recipient_email"),
                rs.getString("remarks"),
                rs.getObject(
                        "created_at",
                        LocalDateTime.class
                ).atOffset(ZoneOffset.UTC),
                rs.getString("created_by_username")
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Cannot serialize document snapshot",
                    exception
            );
        }
    }

    private <T> T fromJson(String value, Class<T> type) {
        if (value == null) {
            return null;
        }

        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Cannot read document snapshot",
                    exception
            );
        }
    }
}