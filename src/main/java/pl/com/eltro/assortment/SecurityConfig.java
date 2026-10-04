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
                                "/api/customers",
                                "/api/sales",
                                "/api/purchase-orders",
                                "/api/sales/*/documents"
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

                .exceptionHandling(exceptions -> exceptions
                        .accessDeniedHandler((request, response, exception) -> {
                            String path = request.getServletPath();

                            boolean csrfError =
                                    exception instanceof InvalidCsrfTokenException
                                            || exception instanceof MissingCsrfTokenException;

                            boolean loginSubmission =
                                    "/login".equals(path)
                                            && "POST".equalsIgnoreCase(
                                            request.getMethod()
                                    );

                            if (csrfError && loginSubmission) {
                                response.sendRedirect(
                                        request.getContextPath()
                                                + "/login?expired"
                                );
                                return;
                            }

                            if (path.startsWith("/api/")) {
                                response.setStatus(
                                        HttpStatus.FORBIDDEN.value()
                                );
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

                            response.setStatus(
                                    HttpStatus.FORBIDDEN.value()
                            );
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
                                            <img src="eltro-logo.png" alt="Eltro">
                                        </header>
                                        <main>
                                            <h1>Brak dostępu</h1>
                                            <p>Nie możesz wykonać tej operacji.
                                               Wróć do panelu lub zaloguj się ponownie.</p>
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