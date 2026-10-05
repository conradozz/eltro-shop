package pl.com.eltro.assortment;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

public class DemoLoginFilter
        extends AbstractAuthenticationProcessingFilter {

    public DemoLoginFilter(AuthenticationManager authenticationManager) {
        super(
                new AntPathRequestMatcher("/login/demo", "POST"),
                authenticationManager
        );
    }

    @Override
    public Authentication attemptAuthentication(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws AuthenticationException {
        UsernamePasswordAuthenticationToken token =
                UsernamePasswordAuthenticationToken.unauthenticated(
                        "demo",
                        ""
                );

        token.setDetails(
                new WebAuthenticationDetailsSource()
                        .buildDetails(request)
        );

        return getAuthenticationManager().authenticate(token);
    }
}