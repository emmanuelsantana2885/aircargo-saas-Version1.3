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

# ── Java: prefer a JDK 21 (el proyecto compila a release 21) ────
# El `java` por defecto del sistema puede ser 25 (Fedora/Arch) mientras
# el javac es 21 → Maven falla con "release version 21 not supported".
# Detectamos JAVA_HOME si no está ya fijado.
if [ -z "${JAVA_HOME:-}" ]; then
  for jdk in \
    "$HOME/.sdkman/candidates/java/21" \
    /usr/lib/jvm/java-21-amazon-corretto \
    /usr/lib/jvm/java-21-openjdk* \
    /usr/lib/jvm/temurin-21* \
    /usr/lib/jvm/jdk-21*; do
    if [ -x "$jdk/bin/javac" ]; then JAVA_HOME="$jdk"; break; fi
  done
  if [ -z "${JAVA_HOME:-}" ]; then
    # Fallback: derivar JAVA_HOME del java de PATH
    JAVAC_BIN="$(command -v javac 2>/dev/null || true)"
    if [ -n "$JAVAC_BIN" ]; then
      JAVA_HOME="$(cd "$(dirname "$JAVAC_BIN")/.." && pwd)"
    fi
  fi
fi
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  export JAVA_HOME
  export PATH="$JAVA_HOME/bin:$PATH"
fi

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
