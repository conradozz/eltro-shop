package pl.com.eltro.assortment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private final JdbcTemplate jdbcTemplate;

    public CurrentUserService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long requireUserId() {
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Authentication is required");
        }

        return jdbcTemplate.query(
                """
                SELECT id
                FROM users
                WHERE username = ? AND enabled = TRUE
                """,
                (rs, rowNum) -> rs.getLong("id"),
                authentication.getName()
        ).stream().findFirst().orElseThrow(() ->
                new AccessDeniedException(
                        "Current user does not exist or is disabled"
                )
        );
    }
}