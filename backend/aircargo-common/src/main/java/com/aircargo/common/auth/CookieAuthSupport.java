package com.aircargo.common.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;

/**
 * Soporte de autenticación por cookies httpOnly (mitigación de XSS:
 * el token deja de ser legible por JavaScript).
 *
 * · aircargo_at → access token,  Path=/            , vida corta
 * · aircargo_rt → refresh token, Path=/api/auth    , larga vida
 *
 * SameSite=Lax bloquea el envío cross-site en POST; CORS restringe orígenes.
 */
public final class CookieAuthSupport {

    public static final String ACCESS_COOKIE = "aircargo_at";
    public static final String REFRESH_COOKIE = "aircargo_rt";
    public static final String REFRESH_PATH = "/api/auth";

    private CookieAuthSupport() {
    }

    public static String extractToken(HttpServletRequest request, String name) {
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if (name.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                    return c.getValue();
                }
            }
        }
        return null;
    }

    public static void issue(HttpServletResponse response, boolean secure,
                             String accessToken, long accessMaxAgeSeconds,
                             String refreshToken, long refreshMaxAgeSeconds) {
        if (accessToken != null) {
            response.addHeader("Set-Cookie", build(ACCESS_COOKIE, accessToken, "/", secure, accessMaxAgeSeconds));
        }
        if (refreshToken != null) {
            response.addHeader("Set-Cookie", build(REFRESH_COOKIE, refreshToken, REFRESH_PATH, secure, refreshMaxAgeSeconds));
        }
    }

    public static void clear(HttpServletResponse response, boolean secure) {
        response.addHeader("Set-Cookie", build(ACCESS_COOKIE, "", "/", secure, 0));
        response.addHeader("Set-Cookie", build(REFRESH_COOKIE, "", REFRESH_PATH, secure, 0));
    }

    public static String build(String name, String value, String path, boolean secure, long maxAge) {
        ResponseCookie c = ResponseCookie.from(name, value == null ? "" : value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(path)
                .maxAge(maxAge)
                .build();
        return c.toString();
    }
}
