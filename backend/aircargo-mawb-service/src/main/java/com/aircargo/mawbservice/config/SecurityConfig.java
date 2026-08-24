package com.aircargo.mawbservice.config;

import com.aircargo.common.auth.JwtAuthFilter;
import com.aircargo.common.auth.JwtUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpMethod;
import static org.springframework.security.config.Customizer.withDefaults;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;

@Configuration
@EnableWebSecurity
@Profile("!test")
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtUtil jwtUtil, org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) throws Exception {
        http
            .cors(withDefaults())
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/compliance/**").hasAnyAuthority("ADMIN", "SUPER_USER")
                .requestMatchers(HttpMethod.POST, "/api/label-templates/**").hasAuthority("SUPER_USER")
                .requestMatchers(HttpMethod.PUT, "/api/label-templates/**").hasAuthority("SUPER_USER")
                .requestMatchers(HttpMethod.DELETE, "/api/label-templates/**").hasAuthority("SUPER_USER")
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()
            )
            .exceptionHandling(eh -> eh.authenticationEntryPoint(
                new org.springframework.security.web.authentication.HttpStatusEntryPoint(org.springframework.http.HttpStatus.UNAUTHORIZED)))
            .addFilterBefore(new JwtAuthFilter(jwtUtil, jdbcTemplate), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
