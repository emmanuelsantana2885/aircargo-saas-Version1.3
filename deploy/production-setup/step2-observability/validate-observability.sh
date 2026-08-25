#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────────
# Step 2 Validation: Verify Observability Stack
# ────────────────────────────────────────────────────────────────

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"

log "══════════════════════════════════════════════════════════════"
log "  VALIDACIÓN PASO 2: OBSERVABILIDAD"
log "══════════════════════════════════════════════════════════════"

# 1. Prometheus
log "1/8 Verificando Prometheus..."
wait_for "Prometheus pods ready" "kubectl get pods -n monitoring -l app.kubernetes.io/name=prometheus --field-selector=status.phase=Running | grep -q '2/2'" 300 10

# Check Prometheus API
PROM_URL="http://prometheus-operated.monitoring.svc.cluster.local:9090"
wait_for "Prometheus API" "curl -sf $PROM_URL/-/healthy >/dev/null" 60 5
log "  Prometheus: OK"

# 2. Alertmanager
log "2/8 Verificando Alertmanager..."
wait_for "Alertmanager pods ready" "kubectl get pods -n monitoring -l app.kubernetes.io/name=alertmanager --field-selector=status.phase=Running | grep -q '2/2'" 300 10
log "  Alertmanager: OK"

# 3. Grafana
log "3/8 Verificando Grafana..."
wait_for "Grafana pods ready" "kubectl get pods -n monitoring -l app.kubernetes.io/name=grafana --field-selector=status.phase=Running | grep -q '2/2'" 300 10

GRAFANA_URL="http://grafana.monitoring.svc.cluster.local:3000"
wait_for "Grafana API" "curl -sf $GRAFANA_URL/api/health >/dev/null" 60 5
log "  Grafana: OK"

# 4. Loki
log "4/8 Verificando Loki..."
wait_for "Loki pods ready" "kubectl get pods -n monitoring -l app.kubernetes.io/name=loki --field-selector=status.phase=Running | grep -q '2/2'" 300 10

LOKI_URL="http://loki.monitoring.svc.cluster.local:3100"
wait_for "Loki API" "curl -sf $LOKI_URL/ready >/dev/null" 60 5
log "  Loki: OK"

# 5. Promtail
log "5/8 Verificando Promtail..."
wait_for "Promtail daemonset ready" "kubectl get daemonset -n monitoring promtail -o jsonpath='{.status.numberReady}' | grep -q '^[1-9]'" 120 5
log "  Promtail: OK"

# 6. Tempo
log "6/8 Verificando Tempo..."
wait_for "Tempo pods ready" "kubectl get pods -n monitoring -l app.kubernetes.io/name=tempo --field-selector=status.phase=Running | grep -q '2/2'" 300 10

TEMPO_URL="http://tempo.monitoring.svc.cluster.local:3200"
wait_for "Tempo API" "curl -sf $TEMPO_URL/ready >/dev/null" 60 5
log "  Tempo: OK"

# 7. OpenTelemetry Collector
log "7/8 Verificando OpenTelemetry Collector..."
wait_for "OTel Collector daemonset ready" "kubectl get daemonset -n monitoring opentelemetry-collector -o jsonpath='{.status.numberReady}' | grep -q '^[1-9]'" 120 5
log "  OpenTelemetry Collector: OK"

# 8. ServiceMonitors & PrometheusRules
log "8/8 Verificando ServiceMonitors y Alert Rules..."

SM_COUNT=$(kubectl get servicemonitor -n monitoring -l release=kube-prometheus-stack --no-headers 2>/dev/null | wc -l)
[[ $SM_COUNT -ge 13 ]] || { error "ServiceMonitors insuficientes: $SM_COUNT (esperado >=13)"; exit 1; }
log "  ServiceMonitors: $SM_COUNT"

RULE_COUNT=$(kubectl get prometheusrule -n monitoring -l release=kube-prometheus-stack --no-headers 2>/dev/null | wc -l)
[[ $RULE_COUNT -ge 1 ]] || { error "PrometheusRules no encontradas"; exit 1; }
log "  PrometheusRules: $RULE_COUNT"

# 9. Dashboards
log "9/9 Verificando dashboards..."
DASH_COUNT=$(kubectl get configmap -n monitoring -l grafana_dashboard=1 --no-headers 2>/dev/null | wc -l)
[[ $DASH_COUNT -ge 1 ]] || { error "Dashboards no encontrados"; exit 1; }
log "  Dashboards: $DASH_COUNT"

# 10. Verify metrics flowing
log "10/10 Verificando métricas fluyendo..."
sleep 30  # Wait for scrape
METRICS=$(curl -sf "$PROM_URL/api/v1/query?query=up{namespace=\"aircargo\"}" | jq -r '.data.result | length')
[[ $METRICS -ge 1 ]] || { warn "No hay métricas de aircargo aún (puede tardar unos minutos)"; }

log "══════════════════════════════════════════════════════════════"
log "  VALIDACIÓN PASO 2: EXITOSA"
log "══════════════════════════════════════════════════════════════"