package pl.com.eltro.assortment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final JdbcTemplate jdbcTemplate;
    private final UserPermissionRepository permissionRepository;

    public DatabaseUserDetailsService(
            JdbcTemplate jdbcTemplate,
            UserPermissionRepository permissionRepository
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.permissionRepository = permissionRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        Account account = jdbcTemplate.query(
                """
                SELECT id, username, password_hash, role, enabled
                FROM users
                WHERE username = ?
                """,
                (rs, rowNum) -> new Account(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("role"),
                        rs.getBoolean("enabled")
                ),
                username
        ).stream().findFirst().orElseThrow(() ->
                new UsernameNotFoundException("User not found")
        );

        List<String> authorities = new ArrayList<>();
        authorities.add("ROLE_" + account.role());

        if ("ADMIN".equals(account.role())) {
            Arrays.stream(UserPermission.values())
                    .map(Enum::name)
                    .forEach(authorities::add);
        } else {
            authorities.addAll(
                    permissionRepository.findByUserId(account.id())
            );
        }

        return User.withUsername(account.username())
                .password(account.passwordHash())
                .authorities(authorities.toArray(String[]::new))
                .disabled(!account.enabled())
                .build();
    }

    private record Account(
            long id,
            String username,
            String passwordHash,
            String role,
            boolean enabled
    ) {
    }
}