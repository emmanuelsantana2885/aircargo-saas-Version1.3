package com.aircargo.exportservice.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Motor de fórmulas aritméticas propio (sin dependencias externas).
 *
 * Gramática (parser recursivo descendente):
 *   expr    := term (('+'|'-') term)*
 *   term    := factor (('*'|'/'|'%') factor)*
 *   factor  := unary | unary '^' factor
 *   unary   := ('+'|'-') unary | primary
 *   primary := NUMBER | VARIABLE | FUNCTION '(' args ')'
 *
 * Variables: [Nombre] o Nombre → pre-resueltas por el servicio en un mapa
 * {claveNormalizada → BigDecimal} (campos numéricos de la fila + variables de escenario).
 *
 * Funciones: LbsToKg, KgToLbs, Max, Min, Abs, Round, Projection, Sum, Avg, Count, ChargeableKg.
 * Seguro: SOLO llama a funciones de la allowlist; sin acceso a objetos/métodos arbitrarios.
 */
public final class FormulaEngine {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final long MAX_STEPS = 50_000;

    private final String src;
    private final Map<String, BigDecimal> vars;
    private int pos;
    private long steps;

    private FormulaEngine(String src, Map<String, BigDecimal> vars) {
        this.src = src;
        this.vars = vars;
    }

    /** Evalúa una expresión. Devuelve null si hay variable desconocida o división por cero. */
    public static Double evaluate(String expression, Map<String, BigDecimal> vars) {
        if (expression == null || expression.isBlank()) return null;
        try {
            FormulaEngine p = new FormulaEngine(expression.trim(), vars);
            BigDecimal v = p.parseExpr();
            p.skipWs();
            if (p.pos < p.src.length()) return null;
            return v == null ? null : v.doubleValue();
        } catch (ArithmeticException | IllegalArgumentException e) {
            return null;
        }
    }

    // ── Parser ──────────────────────────────────────────────
    private BigDecimal parseExpr() {
        BigDecimal left = parseTerm();
        while (true) {
            skipWs();
            if (match('+')) left = op(left, parseTerm(), '+');
            else if (match('-')) left = op(left, parseTerm(), '-');
            else return left;
        }
    }

    private BigDecimal parseTerm() {
        BigDecimal left = parseFactor();
        while (true) {
            skipWs();
            if (match('*')) left = op(left, parseFactor(), '*');
            else if (match('/')) left = op(left, parseFactor(), '/');
            else if (match('%')) left = op(left, parseFactor(), '%');
            else return left;
        }
    }

    private BigDecimal parseFactor() {
        if (++steps > MAX_STEPS) throw new IllegalArgumentException("Fórmula demasiado larga");
        skipWs();
        BigDecimal base = parseUnary();
        skipWs();
        if (match('^')) {
            BigDecimal exp = parseFactor();
            if (base == null || exp == null) return null;
            double pow = Math.pow(base.doubleValue(), exp.doubleValue());
            return new BigDecimal(pow, MC);
        }
        return base;
    }

    private BigDecimal parseUnary() {
        skipWs();
        if (match('+')) return parseUnary();
        if (match('-')) {
            BigDecimal v = parseUnary();
            return v == null ? null : v.negate();
        }
        return parsePrimary();
    }

    private BigDecimal parsePrimary() {
        skipWs();
        if (pos < src.length() && Character.isDigit(src.charAt(pos))) return parseNumber();
        if (pos < src.length() && (Character.isLetter(src.charAt(pos)) || src.charAt(pos) == '[')) return parseIdentifier();
        if (match('(')) {
            BigDecimal v = parseExpr();
            skipWs();
            if (!match(')')) throw new IllegalArgumentException("Falta ')'");
            return v;
        }
        throw new IllegalArgumentException("Token inesperado");
    }

    private BigDecimal parseNumber() {
        int start = pos;
        while (pos < src.length() && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.')) pos++;
        try {
            return new BigDecimal(src.substring(start, pos), MC);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Número inválido");
        }
    }

    private BigDecimal parseIdentifier() {
        int start = pos;
        while (pos < src.length() && (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '_' || src.charAt(pos) == '[' || src.charAt(pos) == ']')) pos++;
        String name = src.substring(start, pos);
        skipWs();
        if (pos < src.length() && src.charAt(pos) == '(') {
            pos++;
            List<BigDecimal> args = parseArgs();
            return callFunction(name, args);
        }
        return variable(name);
    }

    private List<BigDecimal> parseArgs() {
        List<BigDecimal> args = new ArrayList<>();
        skipWs();
        if (pos < src.length() && src.charAt(pos) == ')') { pos++; return args; }
        while (true) {
            args.add(parseExpr());
            skipWs();
            if (match(',')) continue;
            if (match(')')) break;
            throw new IllegalArgumentException("Esperaba ',' o ')'");
        }
        return args;
    }

    private void skipWs() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
    }

    private boolean match(char c) {
        if (pos < src.length() && src.charAt(pos) == c) { pos++; return true; }
        return false;
    }

    private BigDecimal variable(String name) {
        String key = normalizeKey(name);
        if (vars.containsKey(key)) return vars.get(key);
        throw new IllegalArgumentException("Variable desconocida: " + name);
    }

    static String normalizeKey(String k) {
        String s = k;
        if (s.startsWith("[") && s.endsWith("]")) s = s.substring(1, s.length() - 1);
        return s.replace(" ", "").trim();
    }

    private BigDecimal op(BigDecimal a, BigDecimal b, char op) {
        if (a == null || b == null) return null;
        switch (op) {
            case '+': return a.add(b, MC);
            case '-': return a.subtract(b, MC);
            case '*': return a.multiply(b, MC);
            case '/':
                if (b.signum() == 0) return null;
                return a.divide(b, 10, RoundingMode.HALF_UP);
            case '%': return a.remainder(b, MC);
            default: throw new IllegalArgumentException("Operador desconocido");
        }
    }

    private BigDecimal callFunction(String fn, List<BigDecimal> args) {
        switch (fn.toLowerCase()) {
            case "lbstokg":   return mult(args, 0, 0.45359237);
            case "kgtolbs":   return mult(args, 0, 2.2046226218);
            case "abs":       return args.isEmpty() || args.get(0) == null ? null : args.get(0).abs(MC);
            case "round":     return roundFn(args);
            case "max":       return bounds(args, true);
            case "min":       return bounds(args, false);
            case "sum":       return sumFn(args);
            case "avg":       return avgFn(args);
            case "count":     return BigDecimal.valueOf(args.stream().filter(java.util.Objects::nonNull).count());
            case "projection":return projectionFn(args);
            case "chargeablekg": return chargeableFn(args);
            default: throw new IllegalArgumentException("Función desconocida: " + fn);
        }
    }

    private BigDecimal mult(List<BigDecimal> args, int idx, double k) {
        if (args.size() <= idx || args.get(idx) == null) return null;
        return args.get(idx).multiply(BigDecimal.valueOf(k), MC);
    }

    private BigDecimal roundFn(List<BigDecimal> args) {
        if (args.isEmpty() || args.get(0) == null) return null;
        int d = args.size() > 1 && args.get(1) != null ? args.get(1).intValue() : 0;
        return args.get(0).setScale(d, RoundingMode.HALF_UP);
    }

    private BigDecimal bounds(List<BigDecimal> args, boolean max) {
        BigDecimal best = null;
        for (BigDecimal v : args) {
            if (v == null) continue;
            best = best == null ? v : (max ? best.max(v) : best.min(v));
        }
        return best;
    }

    private BigDecimal sumFn(List<BigDecimal> args) {
        BigDecimal s = BigDecimal.ZERO;
        boolean any = false;
        for (BigDecimal v : args) if (v != null) { s = s.add(v, MC); any = true; }
        return any ? s : null;
    }

    private BigDecimal avgFn(List<BigDecimal> args) {
        int n = 0;
        BigDecimal s = BigDecimal.ZERO;
        for (BigDecimal v : args) if (v != null) { s = s.add(v, MC); n++; }
        return n == 0 ? null : s.divide(BigDecimal.valueOf(n), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal projectionFn(List<BigDecimal> args) {
        if (args.isEmpty() || args.get(0) == null) return null;
        double rate = args.size() > 1 && args.get(1) != null ? args.get(1).doubleValue() : 0.0;
        return args.get(0).multiply(BigDecimal.ONE.add(BigDecimal.valueOf(rate), MC), MC);
    }

    private BigDecimal chargeableFn(List<BigDecimal> args) {
        if (args.size() < 3) return null;
        BigDecimal l = args.get(0), w = args.get(1), h = args.get(2);
        if (l == null || w == null || h == null) return null;
        BigDecimal vol = l.multiply(w, MC).multiply(h, MC);
        BigDecimal dimWt = vol.divide(BigDecimal.valueOf(366), 4, RoundingMode.HALF_UP);
        BigDecimal pcs = args.size() > 3 ? args.get(3) : BigDecimal.ONE;
        return dimWt.multiply(pcs == null ? BigDecimal.ONE : pcs, MC);
    }
}
