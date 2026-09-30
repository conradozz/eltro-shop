package pl.com.eltro.assortment;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(
        name = "app.bootstrap-admin.enabled",
        havingValue = "true"
)
public class AdminInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    public AdminInitializer(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            Environment environment
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Long userCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users",
                Long.class
        );

        if (userCount != null && userCount > 0) {
            return;
        }

        String password = environment.getProperty(
                "app.bootstrap-admin.password"
        );

        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "Administrator password must be provided"
            );
        }

        if (password.length() < 12 || password.length() > 64) {
            throw new IllegalStateException(
                    "Administrator password must have 12 to 64 characters"
            );
        }

        jdbcTemplate.update(
                """
                INSERT INTO users (
                    username, password_hash, role, enabled
                )
                VALUES (?, ?, 'ADMIN', TRUE)
                """,
                "admin",
                passwordEncoder.encode(password)
        );
    }
}