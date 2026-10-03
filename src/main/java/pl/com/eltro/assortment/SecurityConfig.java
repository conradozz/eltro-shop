package pl.com.eltro.assortment;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.session.HttpSessionEventPublisher;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SessionRegistry sessionRegistry
    ) throws Exception {

        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/login",
                                "/error",
                                "/eltro-logo.png",
                                "/favicon.ico"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/me",
                                "/api/csrf",
                                "/api/products",
                                "/api/products/**",
                                "/api/customers",
                                "/api/customers/**",
                                "/api/sales",
                                "/api/sales/**",
                                "/api/deliveries",
                                "/api/deliveries/**",
                                "/api/shop-settings",
                                "/api/purchase-orders",
                                "/api/purchase-orders/**"
                        ).hasAnyRole("ADMIN", "SELLER")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/customers",
                                "/api/sales",
                                "/api/purchase-orders"
                        ).hasAnyRole("ADMIN", "SELLER")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/products/*/deliveries"
                        ).hasAnyRole("ADMIN", "SELLER")

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/customers/*",
                                "/api/purchase-orders/*"
                        ).hasAnyRole("ADMIN", "SELLER")

                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/customers/*/discount",
                                "/api/purchase-orders/*/ordered",
                                "/api/purchase-orders/*/cancel"
                        ).hasAnyRole("ADMIN", "SELLER")

                        .requestMatchers("/api/**")
                        .hasRole("ADMIN")

                        .anyRequest().authenticated()
                )

                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .failureUrl("/login?error")
                        .defaultSuccessUrl("/", true)
                        .permitAll()
                )

                .sessionManagement(session -> session
                        .maximumSessions(-1)
                        .sessionRegistry(sessionRegistry)
                        .expiredSessionStrategy(event -> {
                            String path = event.getRequest()
                                    .getServletPath();

                            if (path.startsWith("/api/")) {
                                event.getResponse().setStatus(
                                        HttpStatus.UNAUTHORIZED.value()
                                );

                                event.getResponse().setContentType(
                                        "application/json;charset=UTF-8"
                                );

                                event.getResponse().getWriter().write(
                                        """
                                        {"error":"Session expired"}
                                        """
                                );
                            } else {
                                event.getResponse().sendRedirect(
                                        event.getRequest().getContextPath()
                                                + "/login?expired"
                                );
                            }
                        })
                )

                .logout(logout -> logout
                        .logoutSuccessHandler(
                                new HttpStatusReturningLogoutSuccessHandler(
                                        HttpStatus.NO_CONTENT
                                )
                        )
                        .permitAll()
                );

        return http.build();
    }
}