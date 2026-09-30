package pl.com.eltro.assortment;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Locale;
import java.util.Objects;

@Service
public class UserService {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserSummary create(CreateUserRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("User data is required");
        }

        String username = request.username() == null
                ? ""
                : request.username().trim();

        if (!username.matches("[a-zA-Z0-9._-]{3,80}")) {
            throw new IllegalArgumentException(
                    "Username must contain 3-80 characters: "
                            + "letters, digits, dot, underscore or hyphen"
            );
        }

        String password = request.password();

        if (password == null
                || password.isBlank()
                || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException(
                    "Password must contain at least 12 characters "
                            + "and at most 72 UTF-8 bytes"
            );
        }

        String role = request.role() == null
                ? ""
                : request.role().trim().toUpperCase(Locale.ROOT);

        if (!role.equals("ADMIN") && !role.equals("SELLER")) {
            throw new IllegalArgumentException(
                    "Role must be ADMIN or SELLER"
            );
        }

        String passwordHash = passwordEncoder.encode(password);
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();

        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        """
                        INSERT INTO users (
                            username, password_hash, role, enabled
                        )
                        VALUES (?, ?, ?, TRUE)
                        """,
                        Statement.RETURN_GENERATED_KEYS
                );

                statement.setString(1, username);
                statement.setString(2, passwordHash);
                statement.setString(3, role);

                return statement;
            }, keyHolder);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Username already exists"
            );
        }

        long id = Objects.requireNonNull(keyHolder.getKey()).longValue();

        return new UserSummary(id, username, role, true);
    }
}