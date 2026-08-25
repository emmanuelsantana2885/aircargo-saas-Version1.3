#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────────
# Step 2: Install Observability Stack
# Prometheus, Grafana, Loki, Tempo, Alertmanager, kube-state-metrics
# ────────────────────────────────────────────────────────────────

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"

log "══════════════════════════════════════════════════════════════"
log "  PASO 2: INSTALAR STACK OBSERVABILIDAD"
log "══════════════════════════════════════════════════════════════"

# Add Helm repos
log "Agregando repositorios Helm..."
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo add grafana https://grafana.github.io/helm-charts
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update

# ─── 1. kube-prometheus-stack (Prometheus + Grafana + Alertmanager) ───
log "1/5 Desplegando kube-prometheus-stack..."

cat > /tmp/kube-prometheus-values.yaml <<'YAML'
# kube-prometheus-stack values for production
alertmanager:
  enabled: true
  replicas: 2
  persistentVolume:
    enabled: true
    size: 10Gi
    storageClass: fast-ssd
  config:
    global:
      resolve_timeout: 5m
    route:
      group_by: ['alertname', 'namespace', 'service']
      group_wait: 30s
      group_interval: 5m
      repeat_interval: 4h
      receiver: 'default'
    receivers:
      - name: 'default'
        webhook_configs:
          - url: 'http://alertmanager-webhook.aircargo.svc.cluster.local:5001/webhook'
            send_resolved: true

prometheus:
  enabled: true
  replicas: 2
  retention: 30d
  retentionSize: 50GB
  persistentVolume:
    enabled: true
    size: 100Gi
    storageClass: fast-ssd
  resources:
    requests:
      memory: 2Gi
      cpu: 1000m
    limits:
      memory: 4Gi
      cpu: 2000m
  serviceMonitors:
    enabled: true
  ruleFiles:
    enabled: true
  additionalServiceMonitors:
    - name: aircargo-services
      selector:
        matchLabels:
          app: aircargo-gateway
      endpoints:
        - port: http
          path: /actuator/prometheus
          interval: 30s
    - name: aircargo-frontend
      selector:
        matchLabels:
          app: aircargo-frontend
      endpoints:
        - port: http
          path: /metrics
          interval: 30s

grafana:
  enabled: true
  replicas: 2
  persistence:
    enabled: true
    size: 10Gi
    storageClass: fast-ssd
  adminPassword: "CHANGE_ME_GRAFANA_ADMIN_PASSWORD"
  datasources:
    datasources.yaml:
      apiVersion: 1
      datasources:
        - name: Prometheus
          type: prometheus
          url: http://prometheus-operated.monitoring.svc.cluster.local:9090
          access: proxy
          isDefault: true
        - name: Loki
          type: loki
          url: http://loki.monitoring.svc.cluster.local:3100
          access: proxy
        - name: Tempo
          type: tempo
          url: http://tempo.monitoring.svc.cluster.local:3200
          access: proxy
  dashboardProviders:
    dashboardproviders.yaml:
      apiVersion: 1
      providers:
        - name: 'Aircargo'
          orgId: 1
          folder: 'Aircargo'
          type: file
          disableDeletion: false
          updateIntervalSeconds: 10
          allowUiUpdates: true
          options:
            path: /var/lib/grafana/dashboards/aircargo
  dashboards:
    aircargo:
      spring-boot-jvm:
        gnetId: 4701
        revision: 1
        datasource: Prometheus
      spring-boot-2:
        gnetId: 11398
        revision: 1
        datasource: Prometheus
      kubernetes-cluster:
        gnetId: 7249
        revision: 1
        datasource: Prometheus
      node-exporter:
        gnetId: 1860
        revision: 1
        datasource: Prometheus
      rabbitmq:
        gnetId: 10991
        revision: 1
        datasource: Prometheus
      postgresql:
        gnetId: 9628
        revision: 1
        datasource: Prometheus
  sidecar:
    dashboards:
      enabled: true
      label: grafana_dashboard
      folder: Aircargo
    datasources:
      enabled: true
      label: grafana_datasource

# Node Exporter
nodeExporter:
  enabled: true
  resources:
    requests:
      memory: 50Mi
      cpu: 100m
    limits:
      memory: 100Mi
      cpu: 200m

# kube-state-metrics
kubeStateMetrics:
  enabled: true
  resources:
    requests:
      memory: 50Mi
      cpu: 50m

# Prometheus Operator CRDs
prometheusOperator:
  enabled: true
  admissionWebhooks:
    enabled: true
    patch:
      enabled: true
YAML

helm_deploy "kube-prometheus-stack" "prometheus-community/kube-prometheus-stack" "monitoring" "/tmp/kube-prometheus-values.yaml" "true" "600s"

# ─── 2. Loki (Logs) ───
log "2/5 Desplegando Loki..."

cat > /tmp/loki-values.yaml <<'YAML'
loki:
  enabled: true
  replicas: 2
  persistence:
    enabled: true
    size: 50Gi
    storageClass: fast-ssd
  config:
    auth_enabled: false
    ingester:
      chunk_idle_period: 5m
      chunk_retain_period: 30s
      max_chunk_age: 2h
    schema_config:
      configs:
        - from: 2024-01-01
          store: boltdb-shipper
          object_store: filesystem
          schema: v11
          index:
            prefix: index_
            period: 24h
    limits_config:
      retention_period: 30d
      ingestion_rate_mb: 50
      ingestion_burst_size_mb: 100
      per_stream_rate_limit: 10MB
    storage_config:
      boltdb_shipper:
        active_index_directory: /loki/index
        cache_location: /loki/cache
        cache_ttl: 24h
        shared_store: filesystem
      filesystem:
        directory: /loki/chunks
  resources:
    requests:
      memory: 1Gi
      cpu: 500m
    limits:
      memory: 2Gi
      cpu: 1000m

promtail:
  enabled: true
  config:
    clients:
      - url: http://loki.monitoring.svc.cluster.local:3100/loki/api/v1/push
    scrape_configs:
      - job_name: kubernetes-pods
        kubernetes_sd_configs:
          - role: pod
        relabel_configs:
          - source_labels: [__meta_kubernetes_pod_label_app]
            action: keep
            regex: aircargo-.*
          - source_labels: [__meta_kubernetes_pod_name]
            target_label: pod
          - source_labels: [__meta_kubernetes_namespace]
            target_label: namespace
          - source_labels: [__meta_kubernetes_pod_container_name]
            target_label: container
  resources:
    requests:
      memory: 100Mi
      cpu: 50m
    limits:
      memory: 200Mi
      cpu: 100m

# Grafana Loki datasource already configured in kube-prometheus-stack
YAML

helm_deploy "loki" "grafana/loki-stack" "monitoring" "/tmp/loki-values.yaml" "true" "300s"

# ─── 3. Tempo (Traces) ───
log "3/5 Desplegando Tempo..."

cat > /tmp/tempo-values.yaml <<'YAML'
tempo:
  enabled: true
  replicas: 2
  persistence:
    enabled: true
    size: 50Gi
    storageClass: fast-ssd
  config:
    receiver:
      jaeger:
        protocols:
          thrift_http:
          grpc:
          thrrift_binary:
          thrift_compact:
      zipkin:
      otlp:
        protocols:
          http:
          grpc:
    storage:
      trace:
        backend: local
        local:
          path: /var/tempo/traces
        block:
          bloom_filter_false_positive: .05
          index_downsample_bytes: 1000
          encoding: zstd
    retention:
      trace:
        max_age: 720h
    limits:
      ingestion_rate_mb: 50
      ingestion_burst_size_mb: 100
  resources:
    requests:
      memory: 1Gi
      cpu: 500m
    limits:
      memory: 2Gi
      cpu: 1000m

# OpenTelemetry Collector for trace ingestion
opentelemetry-collector:
  enabled: true
  mode: daemonset
  config:
    receivers:
      otlp:
        protocols:
          grpc:
          http:
      jaeger:
        protocols:
          thrift_http:
          grpc:
          thrift_binary:
          thrift_compact:
      zipkin:
    processors:
      batch:
        timeout: 5s
        send_batch_max_size: 1000
      memory_limiter:
        check_interval: 1s
        limit_mib: 512
      resource:
        attributes:
          - key: k8s.cluster.name
            value: "production"
            action: upsert
    exporters:
      tempo:
        endpoint: "tempo.monitoring.svc.cluster.local:4317"
        tls:
          insecure: true
      logging:
        loglevel: debug
    service:
      pipelines:
        traces:
          receivers: [otlp, jaeger, zipkin]
          processors: [memory_limiter, batch, resource]
          exporters: [tempo, logging]
  resources:
    requests:
      memory: 256Mi
      cpu: 100m
    limits:
      memory: 512Mi
      cpu: 500m
YAML

helm_deploy "tempo" "grafana/tempo" "monitoring" "/tmp/tempo-values.yaml" "true" "300s"

# ─── 4. ServiceMonitors for Aircargo Services ───
log "4/5 Creando ServiceMonitors para servicios Aircargo..."

cat > /tmp/servicemonitors.yaml <<'YAML'
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: aircargo-services
  namespace: monitoring
  labels:
    release: kube-prometheus-stack
spec:
  selector:
    matchLabels:
      app: aircargo-gateway
  endpoints:
    - port: http
      path: /actuator/prometheus
      interval: 30s
  namespaceSelector:
    matchNames:
      - aircargo
---
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: aircargo-frontend
  namespace: monitoring
  labels:
    release: kube-prometheus-stack
spec:
  selector:
    matchLabels:
      app: aircargo-frontend
  endpoints:
    - port: http
      path: /metrics
      interval: 30s
  namespaceSelector:
    matchNames:
      - aircargo
---
# PodMonitors for services without Service (if needed)
apiVersion: monitoring.coreos.com/v1
kind: PodMonitor
metadata:
  name: aircargo-backend-pods
  namespace: monitoring
  labels:
    release: kube-prometheus-stack
spec:
  selector:
    matchLabels:
      app: aircargo-auth-service
  podMetricsEndpoints:
    - port: http
      path: /actuator/prometheus
      interval: 30s
  namespaceSelector:
    matchNames:
      - aircargo
---
apiVersion: monitoring.coreos.com/v1
kind: PodMonitor
metadata:
  name: aircargo-flight-pods
  namespace: monitoring
  labels:
    release: kube-prometheus-stack
spec:
  selector:
    matchLabels:
      app: aircargo-flight-service
  podMetricsEndpoints:
    - port: http
      path: /actuator/prometheus
      interval: 30s
  namespaceSelector:
    matchNames:
      - aircargo
---
apiVersion: monitoring.coreos.com/v1
kind: PodMonitor
metadata:
  name: aircargo-booking-pods
  namespace: monitoring
  labels:
    release: kube-prometheus-stack
spec:
  selector:
    matchLabels:
      app: aircargo-booking-service
  podMetricsEndpoints:
    - port: http
      path: /actuator/prometheus
      interval: 30s
  namespaceSelector:
    matchNames:
      - aircargo
---
apiVersion: monitoring.coreos.com/v1
kind: PodMonitor
metadata:
  name: aircargo-mawb-pods
  namespace: monitoring
  labels:
    release: kube-prometheus-stack
spec:
  selector:
    matchLabels:
      app: aircargo-mawb-service
  podMetricsEndpoints:
    - port: http
      path: /actuator/prometheus
      interval: 30s
  namespaceSelector:
    matchNames:
      - aircargo
---
apiVersion: monitoring.coreos.com/v1
kind: PodMonitor
metadata:
  name: aircargo-warehouse-pods
  namespace: monitoring
  labels:
    release: kube-prometheus-stack
spec:
  selector:
    matchLabels:
      app: aircargo-warehouse-service
  podMetricsEndpoints:
    - port: http
      path: /actuator/prometheus
      interval: 30s
  namespaceSelector:
    matchNames:
      - aircargo
---
apiVersion: monitoring.coreos.com/v1
kind: PodMonitor
metadata:
  name: aircargo-uld-pods
  namespace: monitoring
  labels:
    release: kube-prometheus-stack
spec:
  selector:
    matchLabels:
      app: aircargo-uld-service
  podMetricsEndpoints:
    - port: http
      path: /actuator/prometheus
      interval: 30s
  namespaceSelector:
    matchNames:
      - aircargo
---
apiVersion: monitoring.coreos.com/v1
kind: PodMonitor
metadata:
  name: aircargo-load-planning-pods
  namespace: monitoring
  labels:
    release: kube-prometheus-stack
spec:
  selector:
    matchLabels:
      app: aircargo-load-planning-service
  podMetricsEndpoints:
    - port: http
      path: /actuator/prometheus
      interval: 30s
  namespaceSelector:
    matchNames:
      - aircargo
---
apiVersion: monitoring.coreos.com/v1
kind: PodMonitor
metadata:
  name: aircargo-export-pods
  namespace: monitoring
  labels:
    release: kube-prometheus-stack
spec:
  selector:
    matchLabels:
      app: aircargo-export-service
  podMetricsEndpoints:
    - port: http
      path: /actuator/prometheus
      interval: 30s
  namespaceSelector:
    matchNames:
      - aircargo
---
apiVersion: monitoring.coreos.com/v1
kind: PodMonitor
metadata:
  name: aircargo-notification-pods
  namespace: monitoring
  labels:
    release: kube-prometheus-stack
spec:
  selector:
    matchLabels:
      app: aircargo-notification-service
  podMetricsEndpoints:
    - port: http
      path: /actuator/prometheus
      interval: 30s
  namespaceSelector:
    matchNames:
      - aircargo
YAML

kubectl apply -f /tmp/servicemonitors.yaml

# ─── 5. Alert Rules for Aircargo ───
log "5/5 Creando reglas de alerta..."

cat > /tmp/alert-rules.yaml <<'YAML'
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: aircargo-alerts
  namespace: monitoring
  labels:
    release: kube-prometheus-stack
spec:
  groups:
    - name: aircargo.rules
      rules:
        # Service Down
        - alert: AircargoServiceDown
          expr: |
            up{job=~"aircargo-.*", namespace="aircargo"} == 0
          for: 1m
          labels:
            severity: critical
          annotations:
            summary: "Servicio Aircargo caído: {{ $labels.app }}"
            description: "{{ $labels.app }} en namespace {{ $labels.namespace }} no responde hace 1 minuto"
            runbook_url: "https://github.com/aircargo/runbooks/blob/main/service-down.md"
        
        # High Error Rate
        - alert: AircargoHighErrorRate
          expr: |
            sum(rate(http_server_requests_seconds_count{status=~"5..",namespace="aircargo"}[5m])) by (app)
            /
            sum(rate(http_server_requests_seconds_count{namespace="aircargo"}[5m])) by (app)
            > 0.05
          for: 5m
          labels:
            severity: warning
          annotations:
            summary: "Alta tasa de errores en {{ $labels.app }}"
            description: "Tasa de errores 5xx > 5% en {{ $labels.app }} durante 5 minutos"
            runbook_url: "https://github.com/aircargo/runbooks/blob/main/high-error-rate.md"
        
        # High Latency
        - alert: AircargoHighLatency
          expr: |
            histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{namespace="aircargo"}[5m])) by (le, app)) > 2
          for: 5m
          labels:
            severity: warning
          annotations:
            summary: "Latencia alta en {{ $labels.app }}"
            description: "P95 latencia > 2s en {{ $labels.app }} durante 5 minutos"
            runbook_url: "https://github.com/aircargo/runbooks/blob/main/high-latency.md"
        
        # High Memory Usage
        - alert: AircargoHighMemoryUsage
          expr: |
            (container_memory_usage_bytes{namespace="aircargo", container!="POD"} / container_spec_memory_limit_bytes{namespace="aircargo", container!="POD"}) > 0.85
          for: 10m
          labels:
            severity: warning
          annotations:
            summary: "Uso de memoria alto en {{ $labels.container }}"
            description: "Uso de memoria > 85% del límite en {{ $labels.container }} ({{ $labels.pod }})"
            runbook_url: "https://github.com/aircargo/runbooks/blob/main/high-memory.md"
        
        # High CPU Usage
        - alert: AircargoHighCPUUsage
          expr: |
            (sum(rate(container_cpu_usage_seconds_total{namespace="aircargo", container!="POD"}[5m])) by (container) / sum(container_spec_cpu_quota{namespace="aircargo", container!="POD"} / container_spec_cpu_period{namespace="aircargo", container!="POD"}) by (container)) > 0.8
          for: 10m
          labels:
            severity: warning
          annotations:
            summary: "Uso de CPU alto en {{ $labels.container }}"
            description: "Uso de CPU > 80% del límite en {{ $labels.container }} ({{ $labels.pod }})"
            runbook_url: "https://github.com/aircargo/runbooks/blob/main/high-cpu.md"
        
        # Pod Restarts
        - alert: AircargoPodRestarting
          expr: |
            increase(kube_pod_container_status_restarts_total{namespace="aircargo"}[15m]) > 2
          for: 5m
          labels:
            severity: warning
          annotations:
            summary: "Pod reiniciándose frecuentemente: {{ $labels.pod }}"
            description: "El pod {{ $labels.pod }} se ha reiniciado más de 2 veces en 15 minutos"
            runbook_url: "https://github.com/aircargo/runbooks/blob/main/pod-restarts.md"
        
        # Disk Pressure
        - alert: AircargoDiskPressure
          expr: |
            (kubelet_volume_stats_used_bytes{namespace="aircargo"} / kubelet_volume_stats_capacity_bytes{namespace="aircargo"}) > 0.85
          for: 5m
          labels:
            severity: warning
          annotations:
            summary: "Presión de disco en {{ $labels.persistentvolumeclaim }}"
            description: "Uso de disco > 85% en {{ $labels.persistentvolumeclaim }}"
            runbook_url: "https://github.com/aircargo/runbooks/blob/main/disk-pressure.md"
        
        # RabbitMQ Queue Depth
        - alert: RabbitMQHighQueueDepth
          expr: |
            rabbitmq_queue_messages_ready{vhost="/", queue=~".*"} > 10000
          for: 5m
          labels:
            severity: warning
          annotations:
            summary: "Cola RabbitMQ con muchos mensajes: {{ $labels.queue }}"
            description: "La cola {{ $labels.queue }} tiene más de 10k mensajes pendientes"
            runbook_url: "https://github.com/aircargo/runbooks/blob/main/rabbitmq-queue-depth.md"
        
        # PostgreSQL Connections
        - alert: PostgreSQLHighConnections
          expr: |
            (pg_stat_database_numbackends{datname="aircargo_db"} / pg_settings_max_connections{datname="aircargo_db"}) > 0.8
          for: 5m
          labels:
            severity: warning
          annotations:
            summary: "PostgreSQL conexiones altas"
            description: "Uso de conexiones > 80% del máximo"
            runbook_url: "https://github.com/aircargo/runbooks/blob/main/postgres-connections.md"
        
        # Certificate Expiry
        - alert: AircargoCertExpiringSoon
          expr: |
            ssl_cert_not_after_timestamp_seconds - time() < 86400 * 30
          for: 1h
          labels:
            severity: warning
          annotations:
            summary: "Certificado SSL expira pronto: {{ $labels.host }}"
            description: "El certificado para {{ $labels.host }} expira en menos de 30 días"
            runbook_url: "https://github.com/aircargo/runbooks/blob/main/cert-expiry.md"
YAML

kubectl apply -f /tmp/alert-rules.yaml

# ─── 6. Grafana Dashboards for Aircargo (ConfigMaps) ───
log "6/6 Creando dashboards personalizados..."

cat > /tmp/dashboard-overview.yaml <<'YAML'
apiVersion: v1
kind: ConfigMap
metadata:
  name: aircargo-dashboard-overview
  namespace: monitoring
  labels:
    grafana_dashboard: "1"
    grafana_folder: "Aircargo"
data:
  aircargo-overview.json: |
    {
      "dashboard": {
        "title": "Aircargo Overview",
        "tags": ["aircargo", "overview"],
        "timezone": "browser",
        "panels": [
          {
            "title": "Services Status",
            "type": "stat",
            "gridPos": {"x": 0, "y": 0, "w": 6, "h": 4},
            "targets": [
              {
                "expr": "up{job=~\"aircargo-.*\", namespace=\"aircargo\"}",
                "legendFormat": "{{app}}"
              }
            ],
            "fieldConfig": {
              "defaults": {
                "mappings": [
                  {"type": "value", "options": {"0": {"text": "DOWN", "color": "red"}, "1": {"text": "UP", "color": "green"}}}
                ]
              }
            }
          },
          {
            "title": "Request Rate (req/s)",
            "type": "graph",
            "gridPos": {"x": 6, "y": 0, "w": 12, "h": 8},
            "targets": [
              {
                "expr": "sum(rate(http_server_requests_seconds_count{namespace=\"aircargo\"}[1m])) by (app)",
                "legendFormat": "{{app}}"
              }
            ]
          },
          {
            "title": "Error Rate (5xx %)",
            "type": "graph",
            "gridPos": {"x": 18, "y": 0, "w": 6, "h": 8},
            "targets": [
              {
                "expr": "sum(rate(http_server_requests_seconds_count{status=~\"5..\",namespace=\"aircargo\"}[5m])) by (app) / sum(rate(http_server_requests_seconds_count{namespace=\"aircargo\"}[5m])) by (app) * 100",
                "legendFormat": "{{app}}"
              }
            ]
          },
          {
            "title": "Latency P95 (s)",
            "type": "graph",
            "gridPos": {"x": 0, "y": 8, "w": 12, "h": 8},
            "targets": [
              {
                "expr": "histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{namespace=\"aircargo\"}[5m])) by (le, app))",
                "legendFormat": "{{app}}"
              }
            ]
          },
          {
            "title": "Memory Usage %",
            "type": "graph",
            "gridPos": {"x": 12, "y": 8, "w": 12, "h": 8},
            "targets": [
              {
                "expr": "(container_memory_usage_bytes{namespace=\"aircargo\", container!=\"POD\"} / container_spec_memory_limit_bytes{namespace=\"aircargo\", container!=\"POD\"}) * 100",
                "legendFormat": "{{pod}} - {{container}}"
              }
            ]
          },
          {
            "title": "CPU Usage %",
            "type": "graph",
            "gridPos": {"x": 0, "y": 16, "w": 12, "h": 8},
            "targets": [
              {
                "expr": "sum(rate(container_cpu_usage_seconds_total{namespace=\"aircargo\", container!=\"POD\"}[5m])) by (pod) / sum(container_spec_cpu_quota{namespace=\"aircargo\", container!=\"POD\"} / container_spec_cpu_period{namespace=\"aircargo\", container!=\"POD\"}) by (pod) * 100",
                "legendFormat": "{{pod}}"
              }
            ]
          },
          {
            "title": "Pod Restarts (15m)",
            "type": "stat",
            "gridPos": {"x": 12, "y": 16, "w": 12, "h": 8},
            "targets": [
              {
                "expr": "sum(increase(kube_pod_container_status_restarts_total{namespace=\"aircargo\"}[15m])) by (pod)",
                "legendFormat": "{{pod}}"
              }
            ]
          }
        ],
        "time": {
          "from": "now-1h",
          "to": "now"
        },
        "refresh": "30s"
      },
      "overwrite": true
    }
YAML

kubectl apply -f /tmp/dashboard-overview.yaml

log "=== PASO 2 COMPLETADO ==="