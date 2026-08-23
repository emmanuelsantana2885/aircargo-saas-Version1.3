package com.aircargo.authservice.config;

import com.aircargo.common.auth.JwtAuthFilter;
import com.aircargo.common.auth.JwtUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile("!test")
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtUtil jwtUtil,
                                           org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) throws Exception {
        http
            .cors(withDefaults())
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/auth/block/**").hasAnyAuthority("ADMIN", "SUPER_USER")
                .requestMatchers(HttpMethod.POST, "/api/auth/unblock/**").hasAnyAuthority("ADMIN", "SUPER_USER")
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/api/users/**").hasAnyAuthority("ADMIN", "SUPER_USER")
                .requestMatchers("/api/role-permissions/**").hasAnyAuthority("ADMIN", "SUPER_USER")
                .requestMatchers("/api/audit-logs/**").hasAnyAuthority("ADMIN", "SUPER_USER")
                .requestMatchers("/api/sites/**").hasAuthority("SUPER_USER")
                .requestMatchers("/api/commodity-types/**").hasAnyAuthority("ADMIN", "SUPER_USER")
                .anyRequest().authenticated()
            )
            .addFilterBefore(new JwtAuthFilter(jwtUtil, jdbcTemplate), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
