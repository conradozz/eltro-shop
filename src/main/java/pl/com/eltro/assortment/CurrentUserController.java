package pl.com.eltro.assortment;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CurrentUserController {

    @GetMapping("/api/me")
    public CurrentUser getCurrentUser(Authentication authentication) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring(5))
                .toList();

        return new CurrentUser(
                authentication.getName(),
                roles
        );
    }

    public record CurrentUser(
            String username,
            List<String> roles
    ) {
    }
}