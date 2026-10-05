package pl.com.eltro.assortment;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
@ConditionalOnProperty(
        name = "app.demo.enabled",
        havingValue = "true"
)
public class DemoAccountInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final UserPermissionRepository permissionRepository;

    public DemoAccountInitializer(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            UserPermissionRepository permissionRepository
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.permissionRepository = permissionRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<String> existingRoles = jdbcTemplate.query(
                """
                SELECT role
                FROM users
                WHERE username = ?
                """,
                (rs, rowNum) -> rs.getString("role"),
                "demo"
        );

        if (!existingRoles.isEmpty()) {
            if (!"SELLER".equals(existingRoles.get(0))) {
                throw new IllegalStateException(
                        "The demo account must have the SELLER role"
                );
            }

            // Nie nadpisujemy uprawnień ani statusu istniejącego konta.
            return;
        }

        String randomPassword =
                UUID.randomUUID() + "-" + UUID.randomUUID();

        String passwordHash = passwordEncoder.encode(randomPassword);
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO users (
                        username, password_hash, role, enabled
                    )
                    VALUES (?, ?, 'SELLER', TRUE)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );

            statement.setString(1, "demo");
            statement.setString(2, passwordHash);

            return statement;
        }, keyHolder);

        long userId = Objects.requireNonNull(
                keyHolder.getKey()
        ).longValue();

        permissionRepository.replace(
                userId,
                EnumSet.allOf(UserPermission.class)
        );
    }
}