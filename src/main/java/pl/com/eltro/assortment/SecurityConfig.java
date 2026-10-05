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
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
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
                                "/api/users",
                                "/api/users/**",
                                "/users.html",
                                "/api/document-issuer",
                                "/api/document-issuer/**"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/reports/sales/users"
                        ).hasAnyAuthority("ROLE_ADMIN", "REPORT_ALL")

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
                                "/api/purchase-orders/**",
                                "/api/reports/sales",
                                "/api/reports/sales/**",
                                "/api/documents",
                                "/api/documents/**"
                        ).hasAnyRole("ADMIN", "SELLER")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/products"
                        ).hasAnyAuthority("ROLE_ADMIN", "PRODUCT_CREATE")

                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/products/*/details"
                        ).hasAnyAuthority("ROLE_ADMIN", "PRODUCT_EDIT")

                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/products/*/prices"
                        ).hasAnyAuthority(
                                "ROLE_ADMIN",
                                "PRODUCT_PRICE_EDIT"
                        )

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/customers"
                        ).hasAnyAuthority("ROLE_ADMIN", "CUSTOMER_CREATE")

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/customers/*"
                        ).hasAnyAuthority("ROLE_ADMIN", "CUSTOMER_EDIT")

                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/customers/*/discount"
                        ).hasAnyAuthority(
                                "ROLE_ADMIN",
                                "CUSTOMER_DISCOUNT_EDIT"
                        )

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/products/*/deliveries"
                        ).hasAnyAuthority("ROLE_ADMIN", "DELIVERY_CREATE")

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/deliveries/*"
                        ).hasAnyAuthority("ROLE_ADMIN", "DELIVERY_EDIT")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/sales"
                        ).hasAnyAuthority("ROLE_ADMIN", "SALE_CREATE")

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/sales/*"
                        ).hasAnyAuthority("ROLE_ADMIN", "SALE_EDIT")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/purchase-orders"
                        ).hasAnyAuthority("ROLE_ADMIN", "ORDER_MANAGE")

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/purchase-orders/*"
                        ).hasAnyAuthority("ROLE_ADMIN", "ORDER_MANAGE")

                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/purchase-orders/*/ordered",
                                "/api/purchase-orders/*/cancel"
                        ).hasAnyAuthority("ROLE_ADMIN", "ORDER_MANAGE")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/sales/*/documents"
                        ).hasAnyAuthority("ROLE_ADMIN", "DOCUMENT_CREATE")

                        .requestMatchers("/api/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )

                .exceptionHandling(exceptions -> exceptions
                        .accessDeniedHandler((request, response, exception) -> {
                            String path = request.getServletPath();

                            boolean csrfError =
                                    exception instanceof InvalidCsrfTokenException
                                            || exception instanceof MissingCsrfTokenException;

                            if (csrfError
                                    && "/login".equals(path)
                                    && "POST".equalsIgnoreCase(request.getMethod())) {
                                response.sendRedirect(
                                        request.getContextPath()
                                                + "/login?expired"
                                );
                                return;
                            }

                            response.setStatus(HttpStatus.FORBIDDEN.value());

                            if (path.startsWith("/api/")) {
                                response.setContentType(
                                        "application/json;charset=UTF-8"
                                );

                                response.getWriter().write(
                                        csrfError
                                                ? """
                                                  {"error":"Formularz wygasł. Odśwież stronę i spróbuj ponownie."}
                                                  """
                                                : """
                                                  {"error":"Brak uprawnień do wykonania tej operacji."}
                                                  """
                                );
                                return;
                            }

                            response.setContentType(
                                    "text/html;charset=UTF-8"
                            );

                            response.getWriter().write(
                                    """
                                    <!doctype html>
                                    <html lang="pl">
                                    <head>
                                        <meta charset="UTF-8">
                                        <meta name="viewport"
                                              content="width=device-width,initial-scale=1">
                                        <title>Eltro — brak dostępu</title>
                                        <script src="/eltro-demo.js" defer></script>
                                        <style>
                                            body {
                                                margin:0;
                                                background:#f4f5f7;
                                                color:#24272b;
                                                font:16px Arial,sans-serif;
                                            }
                                            header {
                                                padding:20px 30px;
                                                background:white;
                                                border-bottom:1px solid #e6e8eb;
                                            }
                                            header img { width:150px; }
                                            main {
                                                max-width:600px;
                                                margin:70px auto;
                                                padding:30px;
                                                background:white;
                                                border-radius:14px;
                                            }
                                            a { color:#a90000; }
                                        </style>
                                    </head>
                                    <body>
                                        <header>
                                            <img src="/eltro-logo.png" alt="Eltro">
                                        </header>
                                        <main>
                                            <h1>Brak dostępu</h1>
                                            <p>Nie masz uprawnień do tej operacji.</p>
                                            <p><a href="/">Wróć do panelu</a></p>
                                            <p><a href="/login">Przejdź do logowania</a></p>
                                        </main>
                                    </body>
                                    </html>
                                    """
                            );
                        })
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
                            String path = event.getRequest().getServletPath();

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