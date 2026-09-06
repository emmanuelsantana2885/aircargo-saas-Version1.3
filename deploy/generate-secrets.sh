#!/usr/bin/env bash
# Generate production secrets for Aircargo
# Usage: ./generate-secrets.sh [output-file] [--smtp-host HOST] [--smtp-port PORT] [--smtp-user USER] [--smtp-pass PASS] [--smtp-from FROM] [--aws-key ID] [--aws-secret KEY] [--aws-bucket BUCKET] [--aws-region REGION]
#        ./generate-secrets.sh - | kubectl apply -f -   (stream to kubectl, no file written)

set -euo pipefail

# Defaults
OUTPUT_FILE="${1:-aircargo-secrets-generated.yml}"
SMTP_HOST="${SMTP_HOST:-smtp.example.com}"
SMTP_PORT="${SMTP_PORT:-587}"
SMTP_USERNAME="${SMTP_USERNAME:-noreply@example.com}"
SMTP_PASSWORD="${SMTP_PASSWORD:-CHANGE_ME}"
SMTP_FROM="${SMTP_FROM:-noreply@example.com}"
AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID:-}"
AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY:-}"
AWS_S3_BUCKET="${AWS_S3_BUCKET:-}"
AWS_REGION="${AWS_REGION:-us-east-1}"

# Parse named args (shift past positional output file)
shift || true
while [[ $# -gt 0 ]]; do
  case $1 in
    --smtp-host) SMTP_HOST="$2"; shift 2 ;;
    --smtp-port) SMTP_PORT="$2"; shift 2 ;;
    --smtp-user) SMTP_USERNAME="$2"; shift 2 ;;
    --smtp-pass) SMTP_PASSWORD="$2"; shift 2 ;;
    --smtp-from) SMTP_FROM="$2"; shift 2 ;;
    --aws-key) AWS_ACCESS_KEY_ID="$2"; shift 2 ;;
    --aws-secret) AWS_SECRET_ACCESS_KEY="$2"; shift 2 ;;
    --aws-bucket) AWS_S3_BUCKET="$2"; shift 2 ;;
    --aws-region) AWS_REGION="$2"; shift 2 ;;
    -) OUTPUT_FILE="-"; shift ;;  # stdout
    *) echo "Unknown arg: $1"; exit 1 ;;
  esac
done

# Generate secrets
JWT_SECRET=$(openssl rand -base64 64)
POSTGRES_PASSWORD=$(openssl rand -base64 32 | tr -d '/+=' | cut -c1-32)
RABBITMQ_PASSWORD=$(openssl rand -base64 32 | tr -d '/+=' | cut -c1-32)
APP_ENCRYPTION_KEY=$(openssl rand -base64 32)

cat > "$OUTPUT_FILE" <<EOF
apiVersion: v1
kind: Secret
metadata:
  name: aircargo-secrets
  namespace: aircargo
  labels:
    app: aircargo
    component: secrets
    generated-at: "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
type: Opaque
stringData:
  # JWT Secret (HS512) - 64 bytes base64
  JWT_SECRET: "${JWT_SECRET}"
  
  # Database
  POSTGRES_USER: "aircargo_user"
  POSTGRES_PASSWORD: "${POSTGRES_PASSWORD}"
  
  # RabbitMQ
  RABBITMQ_USER: "aircargo"
  RABBITMQ_PASSWORD: "${RABBITMQ_PASSWORD}"
  
  # AES-256-GCM Encryption Key (32 bytes base64) - for MFA secrets, ID docs, signatures
  APP_ENCRYPTION_KEY: "${APP_ENCRYPTION_KEY}"
  
  # SMTP (REQUIRED for MFA emails, notifications, backup alerts)
  SMTP_HOST: "${SMTP_HOST}"
  SMTP_PORT: "${SMTP_PORT}"
  SMTP_USERNAME: "${SMTP_USERNAME}"
  SMTP_PASSWORD: "${SMTP_PASSWORD}"
  SMTP_FROM: "${SMTP_FROM}"
  
  # MFA Policy
  MFA_RESET_ON_STARTUP: "false"
  MFA_MAX_AGE_DAYS: "7"
  
  # Optional: S3 for backup offsite storage
  AWS_ACCESS_KEY_ID: "${AWS_ACCESS_KEY_ID}"
  AWS_SECRET_ACCESS_KEY: "${AWS_SECRET_ACCESS_KEY}"
  AWS_S3_BUCKET: "${AWS_S3_BUCKET}"
  AWS_REGION: "${AWS_REGION}"
EOF

if [[ "$OUTPUT_FILE" != "-" ]]; then
  echo "Generated $OUTPUT_FILE"
  echo ""
  echo "⚠️  IMPORTANT:"
  echo "1. Review SMTP credentials (current: ${SMTP_HOST}:${SMTP_PORT})"
  echo "2. Update AWS credentials if using S3 backups"
  echo "3. Apply with: kubectl apply -f $OUTPUT_FILE"
  echo "4. DELETE THIS FILE after applying (contains secrets!)"
  echo ""
  echo "To apply directly without saving to disk:"
  echo "  $0 - --smtp-host ... | kubectl apply -f -"
else
  echo "Streamed to stdout (no file written)"
fi