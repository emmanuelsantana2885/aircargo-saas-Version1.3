package com.aircargo.common.util;

/**
 * Pseudonimización de direcciones IP para cumplimiento de minimización
 * de datos (Ley 172-13 / GDPR): el último octeto IPv4 (o últimos 64 bits
 * IPv6) se pone a cero antes de persistir en registros de auditoría.
 */
public final class IpAnonymizer {

    private IpAnonymizer() {
    }

    public static String truncate(String ip) {
        if (ip == null || ip.isBlank()) return ip;
        String v = ip.trim();
        if (v.contains(":")) return truncateIpv6(v);
        if (v.contains(".")) return truncateIpv4(v);
        return v;
    }

    private static String truncateIpv4(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return ip; // no es IPv4 válida: dejar tal cual
        parts[3] = "0";
        return String.join(".", parts);
    }

    private static String truncateIpv6(String ip) {
        // conservar prefijo /64: los últimos 4 grupos se ponen a cero
        String[] parts = ip.split(":");
        if (parts.length < 5) return ip;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            sb.append(i >= parts.length - 4 ? "0" : parts[i]);
            if (i < parts.length - 1) sb.append(":");
        }
        return sb.toString();
    }
}
