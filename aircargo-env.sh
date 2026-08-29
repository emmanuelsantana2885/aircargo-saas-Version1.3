#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────────
# Aircargo environment loader — source this from scripts.
# Loads secrets from the gitignored .env at the repo root and
# validates that required variables are present.
#
#   . ./aircargo-env.sh
# ────────────────────────────────────────────────────────────────

set -a
AIR_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -f "$AIR_ROOT/.env" ]; then
  . "$AIR_ROOT/.env"
fi
set +a

if [ -z "${JWT_SECRET:-}" ]; then
  echo "❌ JWT_SECRET no está definido. Crea el archivo .env desde .env.example:" >&2
  echo "     cp .env.example .env" >&2
  echo "   y genera un secreto con: openssl rand -base64 64" >&2
  exit 1
fi

if [ -z "${POSTGRES_PASSWORD:-}" ]; then
  echo "❌ POSTGRES_PASSWORD no está definido en .env" >&2
  exit 1
fi

if [ -z "${RABBITMQ_PASSWORD:-}" ]; then
  echo "❌ RABBITMQ_PASSWORD no está definido en .env" >&2
  exit 1
fi

export JWT_SECRET RABBITMQ_PASSWORD POSTGRES_PASSWORD POSTGRES_DB POSTGRES_USER RABBITMQ_USER

# ── Maven: locate a usable mvn binary ───────────────────────────
# 1) MAVEN_BIN already set (env var)
# 2) mvn on PATH
# 3) IntelliJ bundled Maven (flatpak), SDKMAN, Homebrew y rutas típicas por distro
if [ -z "${MAVEN_BIN:-}" ]; then
  if command -v mvn >/dev/null 2>&1; then
    MAVEN_BIN="$(command -v mvn)"
  else
    for cand in \
      "/var/lib/flatpak/app/com.jetbrains.IntelliJ-IDEA-Community/x86_64/stable/active/files/plugins/maven-plugin/lib/maven3/bin/mvn" \
      "$HOME/.sdkman/candidates/maven/current/bin/mvn" \
      "/usr/share/maven/bin/mvn" \
      "/opt/maven/bin/mvn" \
      "/usr/local/share/maven/bin/mvn" \
      "$HOME/homebrew/opt/maven/bin/mvn" \
      "/opt/homebrew/opt/maven/bin/mvn"; do
      if [ -x "$cand" ]; then MAVEN_BIN="$cand"; break; fi
    done
  fi
fi
export MAVEN_BIN

if [ -z "$MAVEN_BIN" ]; then
  echo "❌ No se encontró Maven (mvn). Instala Maven o exporta MAVEN_BIN=/ruta/a/mvn" >&2
  exit 1
fi
