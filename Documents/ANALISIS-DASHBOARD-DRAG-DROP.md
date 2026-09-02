# Análisis — Dashboard customizable por Drag & Drop (builder de reportes)

> Objetivo: que en el **Dashboard** se puedan **crear reportes arrastrando campos de datos**
> a un tablero en blanco (tipo drag & drop / builder), guardando un tablero **customizable por usuario**.
> Permisos: **OPERATIONS, TRAFFIC, ADMIN, SUPER_USER** (crear/editar/guardar tableros).
> READ_ONLY, WAREHOUSE_ASSISTANT, LOAD_PLANNER → solo **ver** el tablero (lectura).

---

## 1. Estado actual del Dashboard

`DashboardView.vue` (700 L) hoy tiene **2 tabs fijos y codificados**:
- **Flights**: tabla de vuelos (minWidth 1100px + n*80 por commodity), sticky columns, drill-down `FlightDetail`, totales, export CSV.
- **Weight Report**: filtros + tabla + summary cards + export CSV.

No es customizable: los campos/cards están hardcodeados en el template. No hay persistencia de la disposición.

Toda la lógica de cálculo ya está en el componente (funciones `grossLbs`, `netLbs`, `payloadLbs`, `commodityPayload`, `mawbDispatchedWeightLbs`, etc.) y hay fuentes de datos reales vía backend BI.

---

## 2. Concepto propuesto

Un **Dashboard Builder** con tres zonas:

```
┌─────────────────────────────────────────────────────────────┐
│ [Panel de campos (paleta drag&drop)]       │ [Lienzo (canvas) │
│   · Vuelos  · ULDs  · MAWBs  · Piezas      │   del tablero ]  │
│   · Gross  · Net   · Payload · Países      │   contenedor de  │
│   · Fecha  · Estatus · ...                 │   widgets sueltos │
└─────────────────────────────────────────────────────────────┘
```

1. **Paleta de campos** (izquierda): catálogo de "bloques de datos" que se pueden arrastrar.
2. **Lienzo** (tablero): rejilla (CSS Grid) donde se sueltan los campos → cada uno se convierte en un **widget** (KPI card, tabla, gráfico, lista).
3. **Toolbar**: nombre del tablero, guardar/duplicar/nuevo, vista previa, modo edición vs. presentación.

### Tipos de widgets (según el campo arrastrado)
| Campo (paleta) | Widget resultante | Fuente de datos backend |
|----------------|-------------------|-------------------------|
| KPI numérico (Total Net Payload, Total ULDs, Total MAWBs) | Tarjeta KPI grande | `/api/bi/dashboard`, `/api/bi/summary` |
| Vuelos | Tabla de vuelos | `/api/bi/flights`, `store.flights` |
| ULDs | Tabla / contador ULDs | `/api/bi/ulds` |
| MAWBs | Tabla MAWBs | `/api/bi/mawbs` (y `top-mawbs`, `timeline`) |
| Recibos / peso | Tabla de peso | `/api/bi/weight-report`, `weight-summary` |
| Comodities | Gráfico de dona/barras por commodity | `/api/bi/summary.byCommodity` |
| Por ubicación (SDQ/MIA/...) | Barras por ubicación | `/api/bi/by-location` |
| Timeline | Línea de tiempo | `/api/bi/timeline` |
| Performance de vuelo | Ranking | `/api/bi/flight-performance` |
| Movimiento diario | Serie diaria | `/api/bi/daily` |

---

## 3. Permisos por rol (requisito del usuario)

| Rol | Ver tablero | Crear/editar/guardar tablero |
|-----|-------------|------------------------------|
| **OPERATIONS** | ✅ | ✅ |
| **TRAFFIC** | ✅ | ✅ |
| **ADMIN** | ✅ | ✅ |
| **SUPER_USER** | ✅ | ✅ |
| LOAD_PLANNER | ✅ (solo ver) | ❌ |
| WAREHOUSE_ASSISTANT | ✅ (solo ver) | ❌ |
| READ_ONLY | ✅ (solo ver) | ❌ |

- El botón "Nuevo/Editar tablero" y los controles de drag&drop se muestran **solo** para los 4 roles permitidos (`auth.role` ∈ `['OPERATIONS','TRAFFIC','ADMIN','SUPER_USER']`).
- Los que no editan ven el tablero en modo presentación (solo lectura).

---

## 4. Persistencia (backend — tabla nueva)

Se necesita una tabla para guardar las configuraciones de tablero por usuario:

```
dashboard_config (
  id            UUID PK,
  user_id       UUID NOT NULL,          -- dueño del tablero
  name          VARCHAR(120) NOT NULL,
  layout        JSONB NOT NULL,          -- [{ type, fields[], size, pos (row/col), title }]
  is_default    BOOL DEFAULT false,
  visibility    VARCHAR(20) DEFAULT 'private',  -- private | shared
  created_at    TIMESTAMPTZ,
  updated_at    TIMESTAMPTZ
)
```

- **`layout`**: JSONB con la disposición de widgets (tipo, campos, ancho/alto, orden). El frontend serializa el canvas y lo manda en `PUT`.
- Por simplicidad inicial **se puede persistir en `localStorage`** (por usuario en el navegador) como MVP, y pasar a una tabla backend (endpoints CRUD `GET/POST/PUT/DELETE /api/dashboard-config`) cuando se quiera compartir entre equipos/dispositivos. Recomendado: hacer la tabla desde el inicio para portabilidad (misma vista en cualquier dispositivo, clave para la estrategia responsive).

### Endpoints propuestos (opcionales, para persistencia compartida)
- `GET  /api/dashboard-configs?userId=` (lista del usuario)
- `POST /api/dashboard-configs` (crear/guardar) — roles OPERATIONS/TRAFFIC/ADMIN/SUPER_USER
- `PUT  /api/dashboard-configs/{id}` — roles permitidos
- `DELETE /api/dashboard-configs/{id}` — roles permitidos
- `GET /api/dashboard-configs/{id}` — ver para todos

---

## 5. Arquitectura frontend

### Nuevos módulos
- `components/dashboard/DashboardCanvas.vue` — rejilla donde se sueltan widgets (Drag & Drop area).
- `components/dashboard/WidgetRenderer.vue` — renderiza cada widget (KPI / tabla / gráfico) según el `layout`.
- `components/dashboard/FieldPalette.vue` — catálogo de campos arrastrables (con icono + color).
- `components/dashboard/WidgetKpi.vue`, `WidgetTable.vue`, `WidgetChart.vue` — renderizadores concretos.
- `stores/dashboard.js` — Pinia store: carga `layout`, estado edición/presentación, acciones guardar/cargar.
- `utils/dashboardFields.js` — catálogo estático de campos (key, label, widgetType, api, defaultSize).

### Drag & Drop — dos opciones
1. **HTML5 DnD (nativo)**: simple, ideal para MVP en desktop. Limitado en móvil.
2. **vuedraggable / SortableJS**: soporta touch (Pointer Events), reordenar widgets en el canvas, draggable dentro de grid. **Recomendado** porque además necesita funcionar en el móvil del plan responsive.

Para el canvas, un **CSS Grid de 12 columnas** donde cada widget ocupa `col-span N` (configurable 3/6/12) y `row-span`. El drag sobre el lienzo redimensiona/pone el widget en la celda.

### Integración con la vista actual
- Mantener los 2 tabs existentes (Flights, Weight Report) y añadir un **3er tab "Mi Tablero"** (default para los 4 roles autorizados).
- El tab "Mi Tablero" arranca con un layout por defecto (si el usuario aún no guardó uno) preconstruido con los KPIs principales (Total Net Payload, Total ULDs, Total MAWBs) + tabla de vuelos — para que no aparezca en blanco.

---

## 6. Datos disponibles (backend ya expuesto) para alimentar los widgets

| Endpoint | Uso | 
|----------|-----|
| `/api/bi/dashboard` | KPIs globales (vuelos, mawbs, ulds, peso) |
| `/api/bi/summary` | Resumen + `byCommodity` (gráficos) |
| `/api/bi/by-location` | Distribución por ubicación (SDQ/MIA/...) |
| `/api/bi/timeline` | Serie temporal |
| `/api/bi/top-mawbs` | Ranking top MAWBs |
| `/api/bi/flight-performance` | Performance por vuelo |
| `/api/bi/daily` | Movimiento diario |
| `/api/bi/weight-report` + `weight-summary` | Reporte de peso |
| `store.app` (flights/ulds/mawbs/uldAwbs) | Detalle de tablas (ya cargado en el Dashboard) |

---

## 7. Plan de implementación

**Fase A (MVP — desktop)**:
1. `stores/dashboard.js` + `utils/dashboardFields.js` (catálogo de campos + layout por defecto).
2. `DashboardCanvas.vue` con SortableJS (grid 12), `FieldPalette.vue`, `WidgetRenderer.vue`.
3. 3 widgets iniciales: KPI, Tabla de vuelos, Tabla MAWBs.
4. Botón "Editar / Guardar" visible solo para los 4 roles; persistencia en `localStorage` primero.
5. Tab "Mi Tablero" en `DashboardView.vue`.

**Fase B (persistencia compartida)**:
6. Tabla `dashboard_config` (migración Flyway) + DTO + repositorio + endpoints CRUD + gateway route + frontend `api/dashboardConfigs.js`.
7. Restricción derechos: @PreAuthorize OPERATIONS/TRAFFIC/ADMIN/SUPER_USER en mutaciones; lectura para todos.
8. Cambiar persistencia de `localStorage` → backend por `user_id`.

**Fase C (gráficos + responsive)**:
9. Widgets de gráficos (dona por commodity con los colores de `useCommodities`, barras por ubicación, timeline) — reutilizando la paleta de colores existente (los mismos `c.color` del Dashboard actual).
10. Mismo canvas usable en móvil (SortableJS touch) → alineado con el plan responsive.

### Librerías a considerar
- `vuedraggable` (wraps SortableJS) → drag de paleta→canvas y canvas→canvas. Ya el repo no usa ninguna lib de DnD; esta es la estándar con soporte touch.
- Gráficos: `chart.js`/`vue-chartjs` o **reutilizar CSS puro / SVG** para no añadir peso. Para MVP, SVG/CSS nativo (coherente con el estilo terminal/mono de la app y sin dependencias nuevas).

---

## 8. Riesgos / decisiones
- **No romper los tabs actuales**: el builder es un tab adicional; las tablas existentes siguen operativas.
- **Fetch de datos por widget**: cachear las respuestas BI en el store (los endpoints ya toleran TTL de caché 60s en `export-service`).
- **Permisos doble puerta**: además del ocultado de UI, validar en backend con `@PreAuthorize` (defensa en profundidad, patrón ya usado en el proyecto).
- **Semántica de `shared` tableros**: definir si un tablero compartido lo edita solo su dueño o cualquiera de los 4 roles. Recomendación inicial: dueño edita, resto solo ve (MVP); compartir-edición queda para después.
