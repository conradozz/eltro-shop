package pl.com.eltro.assortment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.util.List;

@Configuration
public class DemoLoginSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain demoLoginSecurityFilterChain(
            HttpSecurity http,
            UserDetailsService userDetailsService,
            SessionRegistry sessionRegistry,
            @Value("${app.demo.enabled:false}") boolean demoEnabled
    ) throws Exception {
        HttpSessionCsrfTokenRepository csrfRepository =
                new HttpSessionCsrfTokenRepository();

        HttpSessionSecurityContextRepository contextRepository =
                new HttpSessionSecurityContextRepository();

        AuthenticationManager authenticationManager = authentication -> {
            if (!demoEnabled) {
                throw new BadCredentialsException(
                        "Demo login is disabled"
                );
            }

            UserDetails demo =
                    userDetailsService.loadUserByUsername("demo");

            new AccountStatusUserDetailsChecker().check(demo);

            boolean seller = demo.getAuthorities().stream()
                    .anyMatch(authority ->
                            "ROLE_SELLER".equals(authority.getAuthority())
                    );

            boolean administrator = demo.getAuthorities().stream()
                    .anyMatch(authority ->
                            "ROLE_ADMIN".equals(authority.getAuthority())
                    );

            if (!"demo".equals(demo.getUsername())
                    || !seller
                    || administrator) {
                throw new BadCredentialsException(
                        "Invalid demo account"
                );
            }

            UsernamePasswordAuthenticationToken result =
                    UsernamePasswordAuthenticationToken.authenticated(
                            demo,
                            null,
                            demo.getAuthorities()
                    );

            result.setDetails(authentication.getDetails());
            return result;
        };

        DemoLoginFilter filter =
                new DemoLoginFilter(authenticationManager);

        filter.setSecurityContextRepository(contextRepository);

        filter.setSessionAuthenticationStrategy(
                new CompositeSessionAuthenticationStrategy(
                        List.of(
                                new ChangeSessionIdAuthenticationStrategy(),
                                new CsrfAuthenticationStrategy(
                                        csrfRepository
                                ),
                                new RegisterSessionAuthenticationStrategy(
                                        sessionRegistry
                                )
                        )
                )
        );

        filter.setAuthenticationSuccessHandler(
                new SimpleUrlAuthenticationSuccessHandler("/")
        );

        filter.setAuthenticationFailureHandler(
                new SimpleUrlAuthenticationFailureHandler(
                        "/login?demoError"
                )
        );

        http
                .securityMatcher(
                        new AntPathRequestMatcher(
                                "/login/demo",
                                "POST"
                        )
                )
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().permitAll()
                )
                .securityContext(context -> context
                        .securityContextRepository(contextRepository)
                )
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                )
                .exceptionHandling(exceptions -> exceptions
                        .accessDeniedHandler(
                                (request, response, exception) ->
                                        response.sendRedirect(
                                                request.getContextPath()
                                                        + "/login?expired"
                                        )
                        )
                )
                .addFilterAt(
                        filter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}