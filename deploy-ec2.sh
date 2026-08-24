#!/bin/bash
set -euo pipefail

# ─── Aircargo SaaS — EC2 Deployment Script ───
# Run this on a fresh Ubuntu 24.04/26.04 EC2 instance (t3.medium+ recommended)
# Usage: curl -sL <raw-url>/deploy-ec2.sh | bash
#   or:  chmod +x deploy-ec2.sh && ./deploy-ec2.sh

echo "═══════════════════════════════════════════════"
echo "  Aircargo SaaS — EC2 Deployment"
echo "═══════════════════════════════════════════════"

# ── 1. System updates + Docker ──────────────────
echo ""
echo "▸ Updating system packages..."
sudo apt-get update -qq
sudo apt-get install -y -qq ca-certificates curl gnupg lsb-release git

echo "▸ Installing Docker..."
if ! command -v docker &>/dev/null; then
  sudo install -m 0755 -d /etc/apt/keyrings
  sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  sudo chmod a+r /etc/apt/keyrings/docker.asc
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
  sudo apt-get update -qq
  sudo apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
  sudo usermod -aG docker ubuntu
  echo "✓ Docker installed"
else
  echo "✓ Docker already installed"
fi

# ── 2. Clone repo ──────────────────────────────
APP_DIR="$HOME/aircargo-saas"
REPO_URL="https://github.com/emmanuelsantana2885/aircargo-saas-Version1.3.git"

if [ -d "$APP_DIR/.git" ]; then
  echo "▸ Pulling latest changes..."
  cd "$APP_DIR"
  git pull origin main
else
  echo "▸ Cloning repository..."
  git clone "$REPO_URL" "$APP_DIR"
  cd "$APP_DIR"
fi

# ── 3. Generate .env if missing ────────────────
if [ ! -f "$APP_DIR/.env" ] || grep -q "CHANGE_ME" "$APP_DIR/.env" 2>/dev/null; then
  echo "▸ Generating .env with secure values..."

  # Get public IP for CORS
  PUBLIC_IP=$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4 2>/dev/null || echo "YOUR_PUBLIC_IP")

  DB_PASS=$(openssl rand -hex 16)
  JWT_SECRET=$(openssl rand -hex 32)
  RMQ_PASS=$(openssl rand -hex 16)
  ENC_KEY=$(openssl rand -base64 32)

  cat > "$APP_DIR/.env" <<EOF
# ── PostgreSQL ─────────────────────────────────────
POSTGRES_DB=aircargo
POSTGRES_USER=aircargo_user
POSTGRES_PASSWORD=${DB_PASS}
POSTGRES_PORT=5432

# ── RabbitMQ ──────────────────────────────────────
RABBITMQ_USER=aircargo
RABBITMQ_PASSWORD=${RMQ_PASS}

# ── JWT (min 32 chars) ────────────────────────────
JWT_SECRET=${JWT_SECRET}

# ── Cifrado en reposo (mfaSecret/cédulas/firmas) ──
APP_ENCRYPTION_KEY=${ENC_KEY}

# ── Reset de contraseñas / cookies ────────────────
FRONTEND_URL=http://${PUBLIC_IP}
COOKIE_SECURE=false

# ── SMTP (mail del cliente) ───────────────────────
SMTP_AUTH=true
SMTP_STARTTLS=true

# ── Ports (host) ──────────────────────────────────
FRONTEND_PORT=80

# ── CORS ──────────────────────────────────────────
CORS_ORIGINS=http://${PUBLIC_IP},http://localhost,http://localhost:80

# ── Java heap (t3.small) ──────────────────────────
JAVA_OPTS=-Xms256m -Xmx512m
EOF

  chmod 600 "$APP_DIR/.env"
  echo "✓ .env created (public IP: ${PUBLIC_IP})"
  echo ""
  echo "  ⚠  Save these credentials:"
  echo "     DB password:       ${DB_PASS}"
  echo "     RabbitMQ password: ${RMQ_PASS}"
  echo "     JWT secret:        ${JWT_SECRET}"
  echo ""
else
  echo "▸ .env already exists, keeping it"
fi

# ── 4. Build and start ─────────────────────────
echo "▸ Building and starting containers (this may take 10-15 min on first run)..."
cd "$APP_DIR"
docker compose down --remove-orphans 2>/dev/null || true
docker compose up -d --build

# ── 5. Wait for health ─────────────────────────
echo "▸ Waiting for services to be healthy..."
for i in {1..60}; do
  if curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1 && \
     curl -sf http://localhost/ > /dev/null 2>&1; then
    echo "  ✓ Gateway and frontend are up after $((i*5))s"
    break
  fi
  sleep 5
  echo "  ... waiting ($((i*5))s)"
done

# ── 6. Summary ─────────────────────────────────
PUBLIC_IP=$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4 2>/dev/null || echo "YOUR_PUBLIC_IP")

echo ""
echo "═══════════════════════════════════════════════"
echo "  ✓ Deployment complete!"
echo "═══════════════════════════════════════════════"
echo ""
echo "  Frontend:  http://${PUBLIC_IP}"
echo "  API:       http://${PUBLIC_IP}:8080"
echo ""
echo "  Status:    docker compose ps"
echo "  Logs:      docker compose logs -f"
echo "  Restart:   docker compose restart"
echo "  Stop:      docker compose down"
echo ""
echo "  Config:    $APP_DIR/.env"
echo ""

# ── 7. Check disk space ────────────────────────
DISK_PCT=$(df / | awk 'NR==2 {print $5}' | tr -d '%')
if [ "$DISK_PCT" -gt 85 ]; then
  echo "  ⚠  WARNING: Disk usage is ${DISK_PCT}%!"
  echo "     Consider resizing the EBS volume."
fi
