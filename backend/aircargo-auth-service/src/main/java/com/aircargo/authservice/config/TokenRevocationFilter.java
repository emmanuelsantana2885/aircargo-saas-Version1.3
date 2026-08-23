package com.aircargo.authservice.config;

import com.aircargo.authservice.service.TokenRevocationService;
import com.aircargo.common.auth.JwtUtil;
import com.aircargo.common.auth.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;

/**
 * Revocación central por-request (solo auth-service, tras JwtAuthFilter):
 * si el token fue emitido antes de tokens_valid_from del usuario → 401.
 * Los demás servicios siguen stateless; su ventana máxima de exposición
 * queda acotada por app.jwt.expiration-ms.
 */
public class TokenRevocationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final TokenRevocationService revocationService;

    public TokenRevocationFilter(JwtUtil jwtUtil, TokenRevocationService revocationService) {
        this.jwtUtil = jwtUtil;
        this.revocationService = revocationService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            String bearer = request.getHeader("Authorization");
            if (bearer != null && bearer.startsWith("Bearer ")) {
                OffsetDateTime iat = jwtUtil.getIssuedAt(bearer.substring(7));
                if (iat != null && revocationService.isStale(principal.getUserIdAsUuid(), iat)) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Session revoked\",\"status\":401}");
                    return;
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
