#!/usr/bin/env bash
# Generate production secrets for Aircargo
# Outputs a secrets.yml file (DO NOT COMMIT TO GIT)

set -euo pipefail

OUTPUT_FILE="${1:-aircargo-secrets-generated.yml}"

cat > "$OUTPUT_FILE" <<EOF
apiVersion: v1
kind: Secret
metadata:
  name: aircargo-secrets
  namespace: aircargo
type: Opaque
stringData:
  # JWT Secret - generate with: openssl rand -base64 64
  JWT_SECRET: "$(openssl rand -base64 64)"
  
  # Database
  POSTGRES_USER: "aircargo_user"
  POSTGRES_PASSWORD: "$(openssl rand -base64 32 | tr -d '/+=' | cut -c1-32)"
  
  # RabbitMQ
  RABBITMQ_USER: "aircargo"
  RABBITMQ_PASSWORD: "$(openssl rand -base64 32 | tr -d '/+=' | cut -c1-32)"
  
  # AES-256-GCM Encryption Key - generate with: openssl rand -base64 32
  APP_ENCRYPTION_KEY: "$(openssl rand -base64 32)"
  
  # SMTP (configure with your values)
  SMTP_HOST: "smtp.example.com"
  SMTP_PORT: "587"
  SMTP_USERNAME: "noreply@example.com"
  SMTP_PASSWORD: "CHANGE_ME_SMTP_PASSWORD"
  SMTP_FROM: "noreply@example.com"
  
  # Optional: S3 for backups
  AWS_ACCESS_KEY_ID: ""
  AWS_SECRET_ACCESS_KEY: ""
  AWS_S3_BUCKET: ""
  AWS_REGION: "us-east-1"
EOF

echo "Generated $OUTPUT_FILE"
echo ""
echo "⚠️  IMPORTANT:"
echo "1. Review and update SMTP credentials"
echo "2. Update AWS credentials if using S3 backups"
echo "3. Apply with: kubectl apply -f $OUTPUT_FILE"
echo "4. DELETE THIS FILE after applying (contains secrets!)"
echo ""
echo "To apply directly without saving to disk:"
echo "  $0 - | kubectl apply -f -"