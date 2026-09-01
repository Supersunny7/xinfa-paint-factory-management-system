package com.sunny.paintfactory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.sunny.paintfactory.auth.JwtAuthenticationFilter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(errors -> errors.authenticationEntryPoint(
                (request, response, exception) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                .accessDeniedHandler((request,response,exception)->response.sendError(HttpServletResponse.SC_FORBIDDEN,"无权执行此操作")))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/error", "/actuator/health", "/api/v1/system/health", "/api/v1/auth/login").permitAll()
                .requestMatchers("/api/v1/auth/change-password").authenticated()
                .requestMatchers("/api/v1/master-data-import/**","/api/v1/inventory-import/**","/api/v1/reference-data/audit-logs").hasRole("ADMIN")
                .requestMatchers("/api/v1/product-classification/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/users/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/dashboard/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST,"/api/v1/reference-data/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT,"/api/v1/reference-data/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH,"/api/v1/reference-data/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE,"/api/v1/reference-data/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/sales-orders/**").hasAnyRole("ADMIN","SALES")
                .requestMatchers("/api/v1/sales-returns/**").hasAnyRole("ADMIN","SALES")
                .requestMatchers("/api/v1/dispatch-sheets/**").hasAnyRole("ADMIN","DISPATCH")
                .requestMatchers("/api/v1/purchases/**").hasAnyRole("ADMIN","WAREHOUSE")
                .requestMatchers("/api/v1/return-warehouses/**").hasAnyRole("ADMIN","WAREHOUSE")
                .requestMatchers("/api/v1/ledgers/cashflow/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/ledgers/sales/**").hasAnyRole("ADMIN","SALES")
                .requestMatchers("/api/v1/ledgers/**").hasAnyRole("ADMIN","WAREHOUSE")
                .requestMatchers("/api/v1/inventory/**","/api/v1/inventory-reconciliation/**").hasAnyRole("ADMIN","WAREHOUSE")
                .requestMatchers(HttpMethod.POST,"/api/v1/products/*/inventory-adjustments").hasAnyRole("ADMIN","WAREHOUSE")
                .requestMatchers("/api/v1/products/import/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST,"/api/v1/customers/**").hasAnyRole("ADMIN","SALES")
                .requestMatchers(HttpMethod.PUT,"/api/v1/customers/**").hasAnyRole("ADMIN","SALES")
                .requestMatchers(HttpMethod.PATCH,"/api/v1/customers/**").hasAnyRole("ADMIN","SALES")
                .requestMatchers(HttpMethod.DELETE,"/api/v1/customers/**").hasAnyRole("ADMIN","SALES")
                .requestMatchers(HttpMethod.POST,"/api/v1/products/**").hasAnyRole("ADMIN","SALES","WAREHOUSE")
                .requestMatchers(HttpMethod.PUT,"/api/v1/products/**").hasAnyRole("ADMIN","SALES","WAREHOUSE")
                .requestMatchers(HttpMethod.PATCH,"/api/v1/products/**").hasAnyRole("ADMIN","SALES","WAREHOUSE")
                .requestMatchers(HttpMethod.DELETE,"/api/v1/products/**").hasAnyRole("ADMIN","SALES","WAREHOUSE")
                .requestMatchers(HttpMethod.POST,"/api/v1/routes/**","/api/v1/vehicles/**","/api/v1/employees/**").hasAnyRole("ADMIN","DISPATCH")
                .requestMatchers(HttpMethod.PUT,"/api/v1/routes/**","/api/v1/vehicles/**","/api/v1/employees/**").hasAnyRole("ADMIN","DISPATCH")
                .requestMatchers(HttpMethod.PATCH,"/api/v1/routes/**","/api/v1/vehicles/**","/api/v1/employees/**").hasAnyRole("ADMIN","DISPATCH")
                .requestMatchers(HttpMethod.DELETE,"/api/v1/routes/**","/api/v1/vehicles/**","/api/v1/employees/**").hasAnyRole("ADMIN","DISPATCH")
                .requestMatchers(HttpMethod.GET,"/api/v1/reference-data/**","/api/v1/customers/**","/api/v1/products/**","/api/v1/routes/**","/api/v1/vehicles/**","/api/v1/employees/**").hasAnyRole("ADMIN","SALES","WAREHOUSE","DISPATCH")
                .anyRequest().authenticated())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
