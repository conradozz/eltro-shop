package pl.com.eltro.assortment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public class UserPermissionRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserPermissionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<String> findByUserId(long userId) {
        return jdbcTemplate.query(
                """
                SELECT permission_code
                FROM user_permissions
                WHERE user_id = ?
                ORDER BY permission_code
                """,
                (rs, rowNum) -> rs.getString("permission_code"),
                userId
        );
    }

    public boolean lockUser(long userId) {
        return !jdbcTemplate.query(
                "SELECT id FROM users WHERE id = ? FOR UPDATE",
                (rs, rowNum) -> rs.getLong("id"),
                userId
        ).isEmpty();
    }

    public void replace(
            long userId,
            Collection<UserPermission> permissions
    ) {
        jdbcTemplate.update(
                "DELETE FROM user_permissions WHERE user_id = ?",
                userId
        );

        for (UserPermission permission : permissions) {
            jdbcTemplate.update(
                    """
                    INSERT INTO user_permissions (
                        user_id, permission_code
                    )
                    VALUES (?, ?)
                    """,
                    userId,
                    permission.name()
            );
        }
    }
}