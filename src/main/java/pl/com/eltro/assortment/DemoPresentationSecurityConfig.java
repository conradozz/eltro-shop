package pl.com.eltro.assortment;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class DemoPresentationSecurityConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain demoPresentationSecurityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http
                .securityMatcher(
                        "/api/demo-mode",
                        "/eltro-demo.js",
                        "/eltro-demo.css"
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/demo-mode",
                                "/eltro-demo.js",
                                "/eltro-demo.css"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.HEAD,
                                "/eltro-demo.js",
                                "/eltro-demo.css"
                        ).permitAll()
                        .anyRequest().denyAll()
                );

        return http.build();
    }
}