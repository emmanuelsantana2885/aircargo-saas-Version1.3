#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────────
# Step 2 Rollback: Remove Observability Stack
# ────────────────────────────────────────────────────────────────

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"

log "══════════════════════════════════════════════════════════════"
log "  ROLLBACK PASO 2: ELIMINAR STACK OBSERVABILIDAD"
log "══════════════════════════════════════════════════════════════"

warn "Esto eliminará:"
warn "  - kube-prometheus-stack (Prometheus, Grafana, Alertmanager)"
warn "  - Loki + Promtail"
warn "  - Tempo + OpenTelemetry Collector"
warn "  - ServiceMonitors, PrometheusRules, Dashboards"
warn "  - Namespace monitoring y PVCs (datos de métricas/logs/traces)"
echo ""
read -p "¿CONFIRMAS ELIMINACIÓN? Escribe 'ELIMINAR' para confirmar: " confirm
[[ "$confirm" == "ELIMINAR" ]] || { error "Cancelado"; exit 1; }

# Uninstall Helm releases
log "Desinstalando Helm releases..."
helm uninstall kube-prometheus-stack -n monitoring --wait --timeout 300s 2>/dev/null || true
helm uninstall loki -n monitoring --wait --timeout 300s 2>/dev/null || true
helm uninstall tempo -n monitoring --wait --timeout 300s 2>/dev/null || true

# Remove CRDs and custom resources
log "Eliminando ServiceMonitors, PrometheusRules, Dashboards..."
kubectl delete servicemonitor -n monitoring -l release=kube-prometheus-stack --ignore-not-found=true
kubectl delete prometheusrule -n monitoring -l release=kube-prometheus-stack --ignore-not-found=true
kubectl delete configmap -n monitoring -l grafana_dashboard=1 --ignore-not-found=true

# Remove namespace (this will delete PVCs too)
log "Eliminando namespace monitoring..."
kubectl delete namespace monitoring --wait --timeout=300s 2>/dev/null || true

# Clean up any remaining resources
kubectl delete crd -l release=kube-prometheus-stack --ignore-not-found=true 2>/dev/null || true

log "══════════════════════════════════════════════════════════════"
log "  ROLLBACK PASO 2 COMPLETADO"
log "══════════════════════════════════════════════════════════════"