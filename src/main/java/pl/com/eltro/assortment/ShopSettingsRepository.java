package pl.com.eltro.assortment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
public class ShopSettingsRepository {

    private final JdbcTemplate jdbcTemplate;

    public ShopSettingsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public BigDecimal getDefaultMarkupPercent() {
        return jdbcTemplate.queryForObject(
                "SELECT default_markup_percent FROM shop_settings WHERE id = 1",
                BigDecimal.class
        );
    }
}
