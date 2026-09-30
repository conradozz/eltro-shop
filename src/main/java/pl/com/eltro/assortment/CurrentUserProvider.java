package pl.com.eltro.assortment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserProvider {

    private final JdbcTemplate jdbcTemplate;

    public CurrentUserProvider(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long getUserIdOrNull() {
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        // Testy wywołujące serwis bez sesji nie mają użytkownika.
        // Dostęp do operacji przez HTTP chroni SecurityConfig.
        if (authentication == null) {
            return null;
        }

        if (!authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException(
                    "Authenticated user is required"
            );
        }

        return jdbcTemplate.query(
                "SELECT id FROM users WHERE username = ?",
                (rs, rowNum) -> rs.getLong("id"),
                authentication.getName()
        ).stream().findFirst().orElseThrow(() ->
                new IllegalStateException(
                        "Authenticated user does not exist in database"
                )
        );
    }
}