package pl.com.eltro.assortment;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
public class CurrentUserController {

    @GetMapping("/api/me")
    public CurrentUser getCurrentUser(Authentication authentication) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring(5))
                .sorted()
                .toList();

        Set<String> knownPermissions = Arrays.stream(
                        UserPermission.values()
                )
                .map(Enum::name)
                .collect(Collectors.toSet());

        List<String> permissions = roles.contains("ADMIN")
                ? knownPermissions.stream().sorted().toList()
                : authentication.getAuthorities().stream()
                  .map(authority -> authority.getAuthority())
                  .filter(knownPermissions::contains)
                  .distinct()
                  .sorted()
                  .toList();

        return new CurrentUser(
                authentication.getName(),
                roles,
                permissions
        );
    }

    public record CurrentUser(
            String username,
            List<String> roles,
            List<String> permissions
    ) {
    }
}