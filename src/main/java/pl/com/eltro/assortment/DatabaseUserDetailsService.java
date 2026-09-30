package pl.com.eltro.assortment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseUserDetailsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return jdbcTemplate.query(
                        """
                        SELECT username, password_hash, role, enabled
                        FROM users
                        WHERE username = ?
                        """,
                        (rs, rowNum) -> User.withUsername(
                                        rs.getString("username")
                                )
                                .password(rs.getString("password_hash"))
                                .roles(rs.getString("role"))
                                .disabled(!rs.getBoolean("enabled"))
                                .build(),
                        username
                ).stream()
                .findFirst()
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found")
                );
    }
}