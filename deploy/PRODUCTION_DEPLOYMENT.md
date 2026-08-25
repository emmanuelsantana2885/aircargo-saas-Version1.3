# Aircargo SaaS — Production Deployment Guide

## Overview
This guide covers deploying the Aircargo microservices platform to a production Kubernetes cluster.

## Architecture
```
Internet → Ingress (NGINX) → Gateway (Spring Cloud Gateway) → 10 Backend Services
                                              ↓
                                    PostgreSQL + RabbitMQ
```

**Services:**
| Service | Port | Replicas (Prod) | Description |
|---------|------|-----------------|-------------|
| aircargo-gateway | 8080 | 3 | API Gateway, JWT auth, rate limiting |
| aircargo-auth-service | 9092 | 3 | Authentication, users, sites, audit |
| aircargo-flight-service | 9093 | 2 | Flights, airlines, aircraft types |
| aircargo-booking-service | 9094 | 2 | Bookings, AWB assignment |
| aircargo-mawb-service | 9095 | 2 | MAWB/HAWB/DUA compliance |
| aircargo-warehouse-service | 9096 | 2 | Warehouse receipts, PDF/Excel export |
| aircargo-uld-service | 9097 | 2 | ULD management, barcode scanning |
| aircargo-load-planning-service | 9098 | 2 | Load planning, manifest export |
| aircargo-export-service | 9099 | 2 | Read-only analytics/BI |
| aircargo-notification-service | 9100 | 2 | Email notifications, RabbitMQ consumers |
| aircargo-frontend | 80 | 3 | Vue 3 SPA served by nginx |

## Prerequisites
- Kubernetes cluster (v1.28+)
- kubectl configured
- NGINX Ingress Controller installed
- cert-manager for TLS certificates
- External Secrets Operator (optional, for secret management)
- Container registry (GHCR, Docker Hub, ECR, etc.)
- PostgreSQL 16+ (managed service recommended: Cloud SQL, RDS)
- RabbitMQ 4.0+ (managed service recommended)

## Quick Start

### 1. Prepare Secrets
```bash
# Generate secrets
JWT_SECRET=$(openssl rand -base64 64)
POSTGRES_PASSWORD=$(openssl rand -base64 32)
RABBITMQ_PASSWORD=$(openssl rand -base64 32)
APP_ENCRYPTION_KEY=$(openssl rand -base64 32)

# Create secret (or use External Secrets Operator)
kubectl create secret generic aircargo-secrets -n aircargo \
  --from-literal=JWT_SECRET="$JWT_SECRET" \
  --from-literal=POSTGRES_USER=aircargo_user \
  --from-literal=POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
  --from-literal=RABBITMQ_USER=aircargo \
  --from-literal=RABBITMQ_PASSWORD="$RABBITMQ_PASSWORD" \
  --from-literal=APP_ENCRYPTION_KEY="$APP_ENCRYPTION_KEY" \
  --from-literal=SMTP_HOST=smtp.example.com \
  --from-literal=SMTP_PORT=587 \
  --from-literal=SMTP_USERNAME=noreply@example.com \
  --from-literal=SMTP_PASSWORD=your-smtp-password \
  --from-literal=SMTP_FROM=noreply@example.com
```

### 2. Configure Domain
Edit `deploy/k8s/overlays/production/kustomization.yml`:
```yaml
# Update ingress hosts
patchesJson6902:
  - target: ...
    patch: |
      - op: replace
        path: /spec/rules/0/host
        value: api.yourdomain.com
      - op: replace
        path: /spec/tls/0/hosts/0
        value: api.yourdomain.com
```

### 3. Deploy
```bash
# Apply production manifests
kubectl apply -k deploy/k8s/overlays/production

# Wait for all deployments
kubectl wait --for=condition=available deployment --all -n aircargo --timeout=300s

# Verify
kubectl get pods -n aircargo
curl -f https://api.yourdomain.com/actuator/health
curl -f https://yourdomain.com/
```

## Detailed Configuration

### Database (PostgreSQL)
**Option A: In-cluster (current manifests)**
- Uses PVC with 50Gi storage
- Daily backup CronJob at 2 AM UTC
- Configure `storageClassName: fast-ssd` in postgres.yml

**Option B: Managed Service (Recommended for Production)**
```yaml
# Remove postgres Deployment/PVC, use external DB
# Update ConfigMap:
POSTGRES_HOST: "your-cloudsql-instance.region.rds.amazonaws.com"
POSTGRES_PORT: "5432"
POSTGRES_DB: "aircargo_db"
# Secrets already reference POSTGRES_USER/POSTGRES_PASSWORD
```

### RabbitMQ
**Option A: In-cluster (current manifests)**
- Single node with management plugin
- For HA: use RabbitMQ Cluster Operator

**Option B: Managed Service (Recommended)**
```yaml
# Update ConfigMap:
RABBITMQ_HOST: "your-rabbitmq.region.cloudamqp.com"
RABBITMQ_PORT: "5672"
```

### TLS Certificates
**With cert-manager (automatic):**
```yaml
# Add to ingress:
annotations:
  cert-manager.io/cluster-issuer: "letsencrypt-prod"
```

**Manual:**
```bash
kubectl create secret tls aircargo-tls-secret -n aircargo \
  --cert=path/to/fullchain.pem \
  --key=path/to/privkey.pem
```

### External Secrets (Recommended)
```yaml
# Install External Secrets Operator
# Create SecretStore pointing to AWS Secrets Manager / Vault / GCP Secret Manager
# Create ExternalSecret referencing aircargo-secrets
```

## Monitoring & Observability

### Prometheus Metrics
All services expose `/actuator/prometheus`. Add ServiceMonitors:
```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: aircargo-services
  namespace: aircargo
spec:
  selector:
    matchLabels:
      app: aircargo-gateway
  endpoints:
    - port: http
      path: /actuator/prometheus
```

### Logging
- Services log JSON to stdout
- Use Fluent Bit / Vector to ship to Loki / Elasticsearch
- Log rotation handled by container runtime

### Health Checks
| Probe | Path | Interval |
|-------|------|----------|
| Readiness | `/actuator/health` | 10s |
| Liveness | `/actuator/health/liveness` | 30s |
| Startup | `/actuator/health` | 10s (30x) |

## Scaling
- **HPA**: CPU 70% / Memory 80% triggers scale
- **Gateway**: min 3, max 20 replicas
- **Frontend**: min 3, max 30 replicas
- **Backend services**: min 2, max 10 replicas
- **PDB**: minAvailable=1 ensures zero-downtime updates

## Backup & Disaster Recovery

### Database Backup
- CronJob runs daily at 2 AM UTC
- Uploads to S3 (configure AWS credentials in secrets)
- Retains 30 days in S3

### Restore Procedure
```bash
# 1. Scale down services
kubectl scale deployment --all --replicas=0 -n aircargo

# 2. Restore from backup
pg_restore -h postgres -U aircargo_user -d aircargo_db \
  s3://your-bucket/postgres-backups/aircargo_db_20240115_020000.dump

# 3. Scale up
kubectl scale deployment --all --replicas=3 -n aircargo
```

## Security Checklist
- [ ] All secrets in External Secrets / Vault (not in repo)
- [ ] TLS 1.2+ enforced on all ingress
- [ ] NetworkPolicies applied (default deny)
- [ ] Non-root containers (UID 1000)
- [ ] Read-only root filesystem where possible
- [ ] PodSecurityPolicy / PSP / Kyverno policies
- [ ] Regular Trivy scans in CI/CD
- [ ] JWT_SECRET rotated quarterly
- [ ] APP_ENCRYPTION_KEY backed up securely
- [ ] Audit logs shipped to SIEM

## Troubleshooting

### Gateway 502/504
```bash
# Check gateway logs
kubectl logs -n aircargo -l app=aircargo-gateway --tail=100

# Check downstream service health
kubectl get pods -n aircargo -l app=aircargo-auth-service
curl -f http://aircargo-auth-service:9092/actuator/health
```

### Database Connection Issues
```bash
# Check postgres logs
kubectl logs -n aircargo -l app=postgres --tail=100

# Test connectivity
kubectl exec -n aircargo -it deploy/aircargo-auth-service -- \
  pg_isready -h postgres -U aircargo_user
```

### RabbitMQ Issues
```bash
# Check queue status
kubectl exec -n aircargo -it deploy/rabbitmq -- \
  rabbitmqctl list_queues name messages consumers
```

## Rolling Updates
```bash
# Update single service
kubectl set image deployment/aircargo-auth-service \
  aircargo-auth-service=ghcr.io/your-org/aircargo-auth-service:v1.2.3 -n aircargo

# Watch rollout
kubectl rollout status deployment/aircargo-auth-service -n aircargo

# Rollback if needed
kubectl rollout undo deployment/aircargo-auth-service -n aircargo
```

## Cost Optimization
- Use spot instances for stateless services (frontend, export)
- Enable cluster autoscaler
- Right-size resource requests/limits based on VPA recommendations
- Use managed PostgreSQL/RabbitMQ to reduce ops burden

## Support
- Check service logs: `kubectl logs -n aircargo -l app=<service> -f`
- Metrics: Grafana dashboards (import Spring Boot dashboards)
- Traces: Jaeger/Zipkin if OpenTelemetry configured