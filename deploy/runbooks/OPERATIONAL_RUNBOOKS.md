# Aircargo Operational Runbooks

## Table of Contents
1. [Incident Response](#incident-response)
2. [Disaster Recovery](#disaster-recovery)
3. [Scaling Operations](#scaling-operations)
4. [Database Operations](#database-operations)
5. [Security Incidents](#security-incidents)
6. [Release Management](#release-management)
7. [Monitoring & Alerting](#monitoring--alerting)

---

## Incident Response

### Severity Levels
| Level | Definition | Response Time | Escalation |
|-------|------------|---------------|------------|
| **SEV-1** | Complete outage, data loss, security breach | 15 min | Page on-call + manager |
| **SEV-2** | Major feature down, degraded performance | 1 hour | Page on-call |
| **SEV-3** | Minor issue, workaround exists | 4 hours | Assign to team |
| **SEV-4** | Cosmetic, low impact | Next sprint | Track in backlog |

### Incident Command Structure
```
Incident Commander (IC) → Owns the incident, coordinates response
  ├── Communications Lead → Status page, stakeholder updates
  ├── Technical Lead → Root cause analysis, fix implementation
  └── Scribe → Timeline documentation, action items
```

### Incident Response Flow
1. **Detection** → Alert fires (Prometheus/Falco) or user report
2. **Triage** → IC acknowledges, assigns severity, creates incident channel
3. **Investigation** → Technical lead gathers logs, traces, metrics
4. **Mitigation** → Apply workaround/rollback to restore service
5. **Resolution** → Root cause fix deployed
6. **Postmortem** → Blameless postmortem within 48h

### Key Commands for Triage
```bash
# Quick health check
kubectl get pods -n aircargo -o wide
kubectl get pods -n logging -o wide
kubectl get pods -n tracing -o wide

# Service-specific logs
kubectl logs -n aircargo -l app=aircargo-gateway --tail=100 -f
kubectl logs -n aircargo -l app=aircargo-auth-service --tail=100 -f

# Check recent events
kubectl get events -n aircargo --sort-by=.metadata.creationTimestamp

# Check HPA status
kubectl get hpa -n aircargo

# Check resource usage
kubectl top pods -n aircargo
kubectl top nodes
```

---

## Disaster Recovery

### RTO/RPO Targets
| Component | RTO | RPO | Strategy |
|-----------|-----|-----|----------|
| **PostgreSQL** | 30 min | 1 hour | Automated backups + Point-in-time recovery |
| **RabbitMQ** | 1 hour | 0 | Quorum queues, message persistence |
| **Redis** | 15 min | 1 hour | AOF + RDB snapshots |
| **Kubernetes** | 2 hours | N/A | GitOps (ArgoCD/Flux) + etcd backup |
| **Secrets** | 5 min | 0 | External Secrets Operator + Vault |

### Database Restore Procedure
```bash
# 1. Identify backup to restore
aws s3 ls s3://aircargo-backups-prod/postgres/ --recursive

# 2. Scale down all services (prevent writes)
kubectl scale deployment --all --replicas=0 -n aircargo

# 3. Restore from backup (Point-in-time recovery)
# Option A: AWS RDS
aws rds restore-db-instance-to-point-in-time \
  --source-db-instance-identifier aircargo-prod \
  --target-db-instance-identifier aircargo-restored \
  --restore-time 2024-01-15T14:30:00.000Z

# Option B: pg_restore from S3 dump
kubectl run pg-restore --rm -i --restart=Never \
  --image=postgres:16-alpine \
  --namespace=aircargo \
  -- pg_restore -h aircargo-restored.cluster-xxxxx.us-east-1.rds.amazonaws.com \
    -U aircargo_user -d aircargo \
    s3://aircargo-backups-prod/postgres/aircargo_20240115_143000.dump

# 4. Verify data integrity
kubectl exec -n aircargo deploy/aircargo-auth-service -- \
  pg_isready -h aircargo-restored.cluster-xxxxx.us-east-1.rds.amazonaws.com

# 5. Update ConfigMap with new endpoint (if changed)
kubectl patch configmap aircargo-config -n aircargo \
  -p '{"data":{"POSTGRES_HOST":"aircargo-restored.cluster-xxxxx.us-east-1.rds.amazonaws.com"}}'

# 6. Scale up services
kubectl scale deployment --all --replicas=3 -n aircargo
kubectl rollout status deployment -n aircargo --timeout=300s

# 7. Run verification
./deploy/verify-deployment.sh
```

### Full Cluster Restore (GitOps)
```bash
# 1. Restore etcd (if using self-managed)
etcdctl snapshot restore /backup/etcd-snapshot.db \
  --data-dir=/var/lib/etcd \
  --initial-cluster=...

# 2. Reinstall ArgoCD/Flux
helm install argocd argo/argo-cd -n argocd

# 3. Applications will auto-sync from Git
kubectl get applications -n argocd

# 4. Verify all resources
kubectl get all -n aircargo
```

---

## Scaling Operations

### Manual Scaling
```bash
# Scale specific service
kubectl scale deployment aircargo-gateway -n aircargo --replicas=5

# Scale all backend services
for svc in auth flight booking mawb warehouse uld load-planning export notification; do
  kubectl scale deployment aircargo-$svc -n aircargo --replicas=3
done

# Scale frontend
kubectl scale deployment aircargo-frontend -n aircargo --replicas=5
```

### HPA Tuning
```bash
# View current HPA
kubectl get hpa -n aircargo -o wide

# Adjust CPU threshold for gateway
kubectl patch hpa aircargo-gateway-hpa -n aircargo \
  -p '{"spec":{"metrics":[{"type":"Resource","resource":{"name":"cpu","target":{"type":"Utilization","averageUtilization":60}}}]}}'

# Adjust memory threshold
kubectl patch hpa aircargo-gateway-hpa -n aircargo \
  -p '{"spec":{"metrics":[{"type":"Resource","resource":{"name":"memory","target":{"type":"Utilization","averageUtilization":70}}}]}}'

# Increase max replicas
kubectl patch hpa aircargo-gateway-hpa -n aircargo \
  -p '{"spec":{"maxReplicas":30}}'
```

### Cluster Autoscaler
```bash
# Check cluster autoscaler status
kubectl logs -n kube-system deployment/cluster-autoscaler

# Scale node group manually (AWS EKS)
aws autoscaling set-desired-capacity \
  --auto-scaling-group-name eks-aircargo-prod-nodegroup \
  --desired-capacity 10
```

---

## Database Operations

### Schema Migrations
```bash
# Check Flyway status per service
for svc in auth flight booking mawb warehouse uld load-planning export notification; do
  echo "=== $svc ==="
  kubectl exec -n aircargo deploy/aircargo-$svc -- \
    java -cp /app.jar org.flywaydb.commandline.Main \
    -url=jdbc:postgresql://$POSTGRES_HOST:5432/aircargo \
    -user=$POSTGRES_USER -password=$POSTGRES_PASSWORD \
    -schemas=$(echo $svc | tr '[:lower:]' '[:upper:]') \
    info
done

# Force baseline (emergency only)
kubectl exec -n aircargo deploy/aircargo-auth-service -- \
  java -cp /app.jar org.flywaydb.commandline.Main \
  -url=jdbc:postgresql://$POSTGRES_HOST:5432/aircargo \
  -user=$POSTGRES_USER -password=$POSTGRES_PASSWORD \
  -schemas=AUTH baseline -baselineVersion=0
```

### Vacuum/Analyze
```bash
# Manual vacuum for bloat
kubectl exec -n aircargo deploy/aircargo-auth-service -- \
  psql -h $POSTGRES_HOST -U $POSTGRES_USER -d aircargo -c "VACUUM ANALYZE;"

# Check table bloat
kubectl exec -n aircargo deploy/aircargo-auth-service -- \
  psql -h $POSTGRES_HOST -U $POSTGRES_USER -d aircargo -c "
  SELECT schemaname, tablename, 
         pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) as size,
         pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename) - pg_relation_size(schemaname||'.'||tablename)) as bloat
  FROM pg_tables 
  WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
  ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC LIMIT 20;"
```

### Connection Pool Monitoring
```bash
# Check active connections per service
kubectl exec -n aircargo deploy/aircargo-auth-service -- \
  psql -h $POSTGRES_HOST -U $POSTGRES_USER -d aircargo -c "
  SELECT datname, usename, application_name, client_addr, state, query_start
  FROM pg_stat_activity 
  WHERE datname = 'aircargo' 
  ORDER BY query_start;"
```

---

## Security Incidents

### Falco Alert Response
```bash
# 1. View recent alerts
kubectl logs -n falco daemonset/falco --tail=200 | grep -E "(CRITICAL|WARNING)" | head -50

# 2. Get alert details from Loki
curl -G "https://loki.logging.svc.cluster.local:3100/loki/api/v1/query_range" \
  --data-urlencode 'query={namespace="falco"} |= "CRITICAL"' \
  --data-urlencode 'limit=100'

# 3. Isolate compromised pod
kubectl label pod <pod-name> -n aircargo quarantine=true
kubectl annotate pod <pod-name> -n aircargo "quarantine.reason=falco-alert"

# 4. Network policy to isolate (if NetworkPolicy exists)
kubectl apply -f - <<EOF
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: quarantine-<pod-name>
  namespace: aircargo
spec:
  podSelector:
    matchLabels:
      quarantine: "true"
  policyTypes:
  - Ingress
  - Egress
  ingress: []
  egress: []
EOF

# 5. Capture forensic data
kubectl cp <pod-name>:/proc/<pid>/exe /tmp/forensics/
kubectl exec -n aircargo <pod-name> -- ps aux > /tmp/forensics/ps_aux.txt
```

### Certificate Issues
```bash
# Check certificate status
kubectl get certificates -n aircargo
kubectl describe certificate aircargo-gateway-tls -n aircargo

# Force renewal
kubectl annotate certificate aircargo-gateway-tls -n aircargo \
  cert-manager.io/force-renewal=$(date +%s) --overwrite

# Check cert-manager logs
kubectl logs -n cert-manager deploy/cert-manager --tail=100 -f
```

### Secret Rotation
```bash
# Rotate JWT secret (requires all pods restart)
# 1. Generate new secret in Vault/Secrets Manager
# 2. Update ExternalSecret (auto-syncs)
# 3. Rolling restart all services
kubectl rollout restart deployment -n aircargo
kubectl rollout status deployment -n aircargo --timeout=300s

# Rotate database password
# 1. Update in RDS/CloudSQL
# 2. Update in External Secrets
# 3. Rolling restart
kubectl rollout restart deployment -n aircargo
```

---

## Release Management

### Pre-Release Checklist
- [ ] All tests pass (unit, integration, contract)
- [ ] Security scan passes (Trivy, dependency-check)
- [ ] Load test passes (P99 < 2s, error rate < 1%)
- [ ] CHANGELOG updated
- [ ] Migration scripts reviewed
- [ ] Rollback plan documented

### Deployment
```bash
# Dry run
./deploy/deploy.sh production --dry-run

# Deploy
./deploy/deploy.sh production

# Verify
./deploy/verify-deployment.sh

# Smoke tests
./scripts/load-test.sh --url https://api.yourdomain.com --token "$TOKEN" --users 10 --duration 30
```

### Rollback
```bash
# Quick rollback (last known good)
kubectl rollout undo deployment/aircargo-auth-service -n aircargo
kubectl rollout status deployment/aircargo-auth-service -n aircargo

# Rollback to specific revision
kubectl rollout undo deployment/aircargo-auth-service -n aircargo --to-revision=5

# Full rollback (all services)
for svc in gateway auth flight booking mawb warehouse uld load-planning export notification frontend; do
  kubectl rollout undo deployment/aircargo-$svc -n aircargo
done
```

### Canary Deployment (ArgoCD)
```yaml
# ArgoCD Application with canary
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: aircargo-gateway-canary
spec:
  source:
    path: deploy/k8s/overlays/production
    helm:
      parameters:
      - name: image.tag
        value: "v1.2.3-canary"
  destination:
    server: https://kubernetes.default.svc
    namespace: aircargo
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
```

---

## Monitoring & Alerting

### Key Dashboards
| Dashboard | URL | Purpose |
|-----------|-----|---------|
| **Aircargo Overview** | Grafana → Dashboards → Aircargo | Service health, JVM, HTTP |
| **Infrastructure** | Grafana → Dashboards → Node Exporter | Node CPU, memory, disk, network |
| **Kubernetes** | Grafana → Dashboards → K8s | Pods, deployments, HPA, resources |
| **PostgreSQL** | Grafana → Dashboards → PG | Connections, queries, bloat, replication |
| **RabbitMQ** | Grafana → Dashboards → RabbitMQ | Queues, consumers, memory, messages |
| **Falco Security** | Grafana → Dashboards → Falco | Runtime alerts, anomalies |

### Alert Routing
| Alert | Channel | Escalation |
|-------|---------|------------|
| SEV-1 (Down, 5xx>5%) | PagerDuty + Slack #aircargo-alerts | 5 min → Manager |
| SEV-2 (Latency, CPU) | Slack #aircargo-alerts | 30 min → Tech Lead |
| SEV-3 (Backup, Cert) | Slack #aircargo-ops | 2 hours → Team |
| Security (Falco) | Slack #aircargo-security + PagerDuty | Immediate |

### Monthly Operational Reviews
- [ ] Review error budgets (SLO compliance)
- [ ] Capacity planning (trends, projections)
- [ ] Security posture (Falco alerts, vuln scans)
- [ ] Cost optimization (right-sizing, unused resources)
- [ ] Backup/restore test results
- [ ] Dependency updates status

---

## Contacts

| Role | Name | Slack | Phone | PagerDuty |
|------|------|-------|-------|-----------|
| Platform Lead | | #platform | | |
| Backend Lead | | #backend | | |
| Security Lead | | #security | | |
| On-Call (Primary) | | | | Rotation |
| On-Call (Secondary) | | | | Rotation |

---

**Document Version**: 1.0  
**Last Updated**: $(date -u +%Y-%m-%d)  
**Next Review**: +30 days