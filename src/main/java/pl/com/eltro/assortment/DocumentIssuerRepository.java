package pl.com.eltro.assortment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentIssuerRepository {

    private final JdbcTemplate jdbcTemplate;

    public DocumentIssuerRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DocumentIssuerSettings getSettings() {
        return jdbcTemplate.queryForObject(
                """
                SELECT company_name, nip, street, postal_code,
                       city, email, phone, bank_account
                FROM document_issuer_settings
                WHERE id = 1
                """,
                (rs, rowNum) -> new DocumentIssuerSettings(
                        rs.getString("company_name"),
                        rs.getString("nip"),
                        rs.getString("street"),
                        rs.getString("postal_code"),
                        rs.getString("city"),
                        rs.getString("email"),
                        rs.getString("phone"),
                        rs.getString("bank_account")
                )
        );
    }

    public void update(DocumentIssuerSettings settings) {
        int updated = jdbcTemplate.update(
                """
                UPDATE document_issuer_settings
                SET company_name = ?,
                    nip = ?,
                    street = ?,
                    postal_code = ?,
                    city = ?,
                    email = ?,
                    phone = ?,
                    bank_account = ?
                WHERE id = 1
                """,
                settings.companyName(),
                settings.nip(),
                settings.street(),
                settings.postalCode(),
                settings.city(),
                settings.email(),
                settings.phone(),
                settings.bankAccount()
        );

        if (updated != 1) {
            throw new IllegalStateException(
                    "Document issuer settings row is missing"
            );
        }
    }
}