package pl.com.eltro.assortment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<UserSummary> userMapper = (rs, rowNum) ->
            new UserSummary(
                    rs.getLong("id"),
                    rs.getString("username"),
                    rs.getString("role"),
                    rs.getBoolean("enabled")
            );

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<UserSummary> findAll() {
        return jdbcTemplate.query(
                """
                SELECT id, username, role, enabled
                FROM users
                ORDER BY username
                """,
                userMapper
        );
    }

    public Optional<UserSummary> findById(long id) {
        return jdbcTemplate.query(
                """
                SELECT id, username, role, enabled
                FROM users
                WHERE id = ?
                """,
                userMapper,
                id
        ).stream().findFirst();
    }

    public void updateEnabled(long id, boolean enabled) {
        jdbcTemplate.update(
                "UPDATE users SET enabled = ? WHERE id = ?",
                enabled,
                id
        );
    }

    public void updatePassword(long id, String passwordHash) {
        jdbcTemplate.update(
                "UPDATE users SET password_hash = ? WHERE id = ?",
                passwordHash,
                id
        );
    }
}