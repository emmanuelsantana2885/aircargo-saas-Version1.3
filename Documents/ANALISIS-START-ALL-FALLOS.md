# Análisis de fallos — `./start-all.sh`

> Auditoría hecha el 2026-09-01 sobre la rama `main` (commit `d9712b9` + cambios sin commitear).
> Se validó cada hallazgo **ejecutando** la lógica real (no solo lectura de código).

Estado del entorno al auditar:

| Recurso | Estado real |
|---------|-------------|
| Postgres `:5432` | Contenedor Docker `aircargo-db` (UP healthy) |
| RabbitMQ `:5672` | **Nativo systemd** (`rabbitmq-server` 4.2.9, activo) — NO hay contenedor rabbitmq |
| Backend `:8080/:9092` | Caído |
| Frontend `:5173` | Caído |
| Docker daemon | OK |

`initdb`/`pg_ctl` **no están** en PATH y no existe `/usr/lib/postgresql/*/bin` (machine Arch) → `find_pg_bins` devuelve vacío.

---

## 🔴 FALLO 1 (CRÍTICO / REGRESIÓN sin commitear): "ceder :5672 al RabbitMQ de Docker" destruye RabbitMQ

### Qué dice el código (`start-all.sh:335-345`, agregado en el working tree sin commitear)

```bash
if [ "$rmq_up" = "true" ] && docker_ok; then
  echo "🔄 Cediendo :$RMQ_PORT al RabbitMQ de Docker (deteniendo instancia existente)..."
  systemctl stop rabbitmq-server 2>/dev/null || true
  pkill -f "rabbitmq-server" 2>/dev/null || true
  sleep 2
  port_up "$RMQ_PORT" || rmq_up=false
fi
```

### Por qué falla (verificado ejecutándolo)

1. **Asume que un RabbitMQ en `:5672` es "instancia conflictiva" que debe ceder su puerto a Docker.** Falso: en esta máquina es el **nativo systemd que está funcionando correctamente** con el usuario/credenciales del `.env` (`.env` → `RABBITMQ_USER/RABBITMQ_PASSWORD`) y persistencia en `/var/lib/rabbitmq`.

2. **`systemctl stop rabbitmq-server` SÍ detiene el servicio** aunque se ejecute como usuario normal (polkit permite el stop sin root). Lo ejecuté: `:5672` dejó de responder, RabbitMQ quedó `inactive`. → El script **apaga el broker sano** antes de saber si lo puede reemplazar.

3. **`pkill -f "rabbitmq-server"` es inefectivo.** El proceso real es `/usr/lib64/erlang/.../beam.smp`, cuyo cmdline **no** contiene el literal `rabbitmq-server` (`pgrep -af "rabbitmq-server"` → 0 coincidencias). Solo mataría a un `rabbitmq-server` que fuese un script en foreground (no el caso con systemd).

4. **No verifica que exista/esté corriendo un contenedor `aircargo-rabbitmq`.** `docker ps` en esta máquina solo muestra `aircargo-db`. Al ceder el puerto:
   - `docker compose -f infrastructure.yml up -d rabbitmq` (línea 403) levanta el contenedor **fresh**.
   - El bind-mount de `enabled_plugins` ya estaba **comentado** en el compose justo por el `eacces` (ownership UID 100 vs 1000, documentado en AGENTS.md) → el contenedor fresh no tiene plugins/venidas previas.
   - Si el contenedor tarda o falla, `rmq_up` queda `false` y el script degrada: `notification-service arranca SIN listeners AMQP`.

### Consecuencia concreta

`./start-all.sh` en el estado actual **apaga el RabbitMQ nativo bueno** y deja la app con `RABBITMQ_ENABLED=false` (sin eventos de recibo/booking/vuelo, sin notificaciones), a la espera de que un contenedor fresh lo reemplace.

### Fix propuesto

El "ceder el puerto" debe ocurrir **solo si realmente hay un contenedor rabbitmq que lo reemplace**, nunca detener el nativo bueno a ciegas:

```bash
# Reemplazar el bloque por: solo ceder si hay un contenedor rabbitmq corriendo
rmq_container=$(docker ps --format '{{.Names}}' 2>/dev/null | grep -x 'aircargo-rabbitmq' || true)
if [ "$rmq_up" = "true" ] && [ -n "$rmq_container" ] && docker_ok; then
  echo "🔄 Sustituyendo RabbitMQ nativo por el contenedor (mismo usuario/credenciales)..."
  systemctl stop rabbitmq-server 2>/dev/null || true
  sleep 2
  port_up "$RMQ_PORT" || rmq_up=false
fi
```

Y en el brazo nativo (línea 409) **no arrancar un segundo nativo** si ya hay uno systemd sano.

---

## 🟠 FALLO 2: `find_pg_bins` no detecta PostgreSQL nativo en Arch/Fedora

### Qué dice el código (`start-all.sh:209-219`)

```bash
if command -v initdb && command -v pg_ctl; then PG_INITDB=...; PG_CTL=...; fi
for dir in /usr/lib/postgresql/*/bin; do [ -x "$dir/initdb" ] && [ -x "$dir/pg_ctl" ] && { ...; }; done
```

### Por qué falla

- En **Arch** los binarios viven en `/usr/bin` y **`initdb`/`pg_ctl` NO se instalan por defecto** con `postgresql` (solo `postgres`, `pg_dump`, `psql`, etc.). En Fedora/Ubuntu tampoco en PATH si se usa el paquete de distro con datadir de sistema.
- `PG_INITDB='' PG_CTL=''` confirmado en esta máquina.
- **Impacto**: si Docker cae y se necesita el fallback nativo, el script aborta con `❌ Ni Docker ni PostgreSQL nativo están disponibles` (línea 365-369) aunque `postgresql` esté instalado y funcione vía systemd. Además el datadir real (`/var/lib/postgres/data`) no lo gestiona el script, de modo que **arrancar un segundo Postgres en `.local-pg` es peligroso** (conflicto de puerto/datos).

### Fix propuesto

Buscar también `/usr/bin/postgres`, `/usr/bin/pg_ctl`, y **reutilizar un Postgres systemd activo** en vez de lanzar uno propio:

```bash
find_pg_bins() {
  PG_INITDB="" PG_CTL=""; PG_BIN=""
  command -v pg_ctl >/dev/null 2>&1 && PG_CTL="$(command -v pg_ctl)"
  for d in /usr/lib/postgresql/*/bin /usr/bin /usr/local/bin; do
    [ -x "$d/initdb" ] && [ -z "$PG_INITDB" ] && PG_INITDB="$d/initdb"
    [ -x "$d/pg_ctl" ] && [ -z "$PG_CTL" ] && PG_CTL="$d/pg_ctl"
    [ -x "$d/postgres" ] && [ -z "$PG_BIN" ] && PG_BIN="$d/postgres"
  done
}
```

Idealmente: si `systemctl is-active postgresql` → usar ese (sin tocar). Solo usar `.local-pg` si no hay ningún Postgres del sistema.

---

## 🟡 FALLO 3: `free_port_if_unhealthy` depende de `grep -oP` (PCRE)

### Qué dice el código (`start-all.sh:150`)

```bash
pids=$(ss -tlnp ... | grep -oP 'pid=\K[0-9]+' ... )
```

### Por qué falla

`grep -P` (PCRE) **no existe en BSD grep** (macOS) y en algunos BusyBox. En la máquina actual (GNU grep) funciona, pero rompe la portabilidad multi-SO que `AGENTS.md` dice mantener (la auditoría del 29-ago incluye WSL2, donde GNU grep sí está, pero en macOS no). Es un fallo latente de portabilidad.

### Fix propuesto

Usar `grep -oE 'pid=[0-9]+'` + `cut -d= -f2`, o `sed -nE 's/.*pid=([0-9]+).*/\1/p'`.

---

## 🟡 FALLO 4: `wait_health` bajo `set -euo pipefail` + bucle `while !`

### Qué dice el código (`start-all.sh:221-237`)

```bash
while ! curl -s ... | grep -q '"status":"UP"'; do
  ...
done
```

### Por qué falla

Con `set -o pipefail`, si **`curl` devuelve código != 0** (p.ej. conexión rechazada, `connection refused`) en el pipeline, el `while` evalúa el exit status conjuntamente y el pipeline devuelve failure → el `while !` puede **salir antes de tiempo** por un error transitorio (curl rc 7) en lugar de un UP real, y aunque el loop siga, cada iteración con curl fallando incrementa `$elapsed` contando hacia el timeout. No es fatal (al final espera el UP), pero hace el health check frágil y puede falsar `timeout`.

Además, la condición `grep -q '"UP"'` no cubre el caso en que `/actuator/health` devuelva `"status":"DOWN"` con otras métricas — correcto, solo cuenta UP.

### Fix propuesto

```bash
while true; do
  if curl -sf "http://localhost:${port}/actuator/health" | grep -q '"status":"UP"'; then break; fi
  ...
done
```
(usar `-sf` para que curl no "falle ruidosamente" bajo pipefail y tolerar reintentos).

---

## 🟢 FALLO 5 (menor / por diseño, documentar): idempotencia parcial y Ctrl+C no para servicios ajenos

- `start_service` omite un servicio que ya responde UP y **no lo registra en `CHILD_PIDS`** → el `monitor` no lo vigila y `Ctrl+C`/`cleanup` no lo detiene (intencional para no matar stacks de otros launchers, pero el usuario puede pensar que "Ctrl+C detiene todo" y no sea así). Documentar en el mensaje final o añadir un aviso.
- `cleanup` solo mata los PIDs registrados; un servicio healthy en otro launcher no se toca (bien), pero **un frontend Vite iniciado por este script sí** se mata (registrado).

---

## ✅ Lo que SÍ funciona (verificado)

- Prerrequisitos con hint por distro (`pkg_hint`/`detect_pkg_mgr`).
- Postgres: con `aircargo-db` contenedor ya UP, se omite correctamente (no recrea BD, no pasa por fallback nativo).
- `--skip-build` correcto; build offline con fallback online.
- Monitor keep-alive resiliente ante caída de un solo servicio.
- Auto-restore por rollback con flag en `/tmp`.

---

## Resumen de prioridad de arreglo

| # | Severidad | Impacto | Arreglar en |
|---|-----------|---------|-------------|
| 1 | 🔴 Crítico | Apaga RabbitMQ nativo bueno → app sin eventos/notificaciones | inmediato |
| 2 | 🟠 Alto | Fallback nativo de Postgres roto en Arch/Fedora | medio |
| 3 | 🟡 Medio | Portabilidad macOS rota (`grep -P`) | medio |
| 4 | 🟡 Medio | Health check frágil bajo pipefail | bajo |
| 5 | 🟢 Bajo | Ctrl+C no para servicios sanos preexistentes | documentar |
