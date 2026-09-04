# Diseño — Dashboard Builder: Reporte Calculable (hoja de cálculo) + Campos Calculados + Gráficos

> Complementa `Documents/ANALISIS-DASHBOARD-DRAG-DROP.md` (tablero drag & drop de widgets).
> Este documento define el **2º pilar**: un **tab "Dashboard Builder"** con un **reporte calculable
> estilo hoja de cálculo** donde se generan reportes/proyecciones a partir de los datos que ya hay
> en la BD y de la **lógica de cálculo del backend**, con **campos calculados** y **gráficos**.
> Permisos: OPERATIONS / TRAFFIC / ADMIN / SUPER_USER (editar). El resto solo ve (lectura).

---

## 1. Concepto: "Reporte Calculable" = mini-spreadsheet

Dentro del tab **Dashboard Builder**, además del tablero de widgets (drag & drop), habrá un
**sub-tab "Reporte"** que muestra una rejilla de datos con filas y columnas, pero donde:
- Las **columnas** son **campos** traídos del backend (Vuelo, ULD, MAWB, Pzas, Gross lbs, Net lbs, Payload, Chargeable, Fecha, Destino...).
- Se puede **añadir columnas calculadas** escritas con una **fórmula** (tipo hoja de cálculo / DAX ligero) que produce valores por fila o agrega al total.
- Se puede **filtrar / pivotar** y **graficar** cualquier columna calculada con Chart.js.

```
Reporte: "Proyección de Payload por Vuelo"

| Vuelo | Tare | GrossLbs | NetLbs = Gross-Tare | Util% = NetLbs/MaxPayload | Proyección = NetLbs * 1.10 |
|-------|------|----------|----------------------|---------------------------|-----------------------------|
| AA123 | 140  | 20.000   | 19.860               | 82%                       | 21.846                      |
| AA124 | 140  | 15.000   | 14.860               | 63%                       | 16.346                      |
```

---

## 2. Arquitectura del motor de cálculo

### 2.1 Motor de fórmulas en el BACKEND (recomendado)
Un **evaluador de expresiones** que recibe:
- **contexto**: mapa `{ tipo_de_fila: { campos } }` (datos ya cargados desde los endpoints BI/warehouse/flight).
- **fórmula**: string evaluado por fila y para agregados (SUM, AVG, MAX, MIN, COUNT, COUNTIF).

Esto centraliza la lógica (defensa en profundidad) y permite que la **misma formula** se use para
generar el reporte y para el gráfico, o para exportar.

Opciones de motor en Java:
| Librería | Pros | Contras | Apto para |
|----------|------|---------|-----------|
| **Exp4j** (`net.objecthunter:exp4j`) | Tiny, ARITMÉTICA + funciones (`sin, abs, max, min, avg...`), sin estados peligrosos, fácil de injectar en BD | Solo aritmética pura; **no** tiene contexto de "columnas"ni agregados por defecto (se resuelven antes) | Escenarios/árboles de operaciones puras |
| **JEXL** / **commons-jexl3** | Expresiones con variables y soporte de funciones/objetos; muy usado en reglas de negocio | Permite ejecutar métodos (hay que restringir por allowlist por seguridad) | Fórmulas con función **por fila** reutilizando helpers del backend |
| **GraalVM JS / Nashorn** | JS completo: `let x = ...`, funciones, arrays | Más intrusivo y más superficie de seguridad | (No recomendado por defecto) |
| **Motor propio (funciones + Parser simple)** | Control total, sin dependencias, testes unitarios | Reimplementar precedencia/paréntesis | Si el conjunto de funciones es pequeño y estable |

**Recomendación**: **exp4j** para el evaluador puro + un **repositorio de "funciones de negocio"** en Java
(`TareOfType`, `LbsToKg`, `ChargeableWeight`, `PayloadUtilization`, `ProjectionAt(rate)`) que se registran
en el motor como funciones nombradas. Esto da fórmulas compactas tipo `NET = GROSS - TareOf(type)`.

### 2.2 Motor en el FRONTEND (para proyección instantánea / offline)
- **Formula.js** (librería de funciones hoja de cálculo, puro JS): `SUM, COUNT, SUMIF, MAX, MIN, ROUND, IF`... Ideal para campos calculados en el navegador con **vista previa en vivo** sin recargar.
- El **cálculo de referencia** final debe hacerse en backend (mismo motor que los reportes BI) para que el resultado sea consistente con el resto de reportes y exportaciones.

**Flujo**: el frontend usa **Formula.js** para una **proyección en vivo** (feedback inmediato al editar), y al **guardar/ejecutar** el reporte se envía la fórmula al backend (exp4j + funciones de negocio) que devuelve el resultado canónico y el gráfico.

---

## 3. El mini-lenguaje de fórmulas (sin SQL, sin DAX pesado)

Definimos un **DSL muy pequeño y seguro**, fácil de aprender, que NO ejecuta código arbitrario:

### Gramática (por fila)
```
<fórmula> ::= <expr> | <agregado>( <expr> )
<expr>    ::= operación aritmética sobre [CAMPO] , literales numéricos, y funciones
<agregado>::= SUM | AVG | MAX | MIN | COUNT | COUNTIF | SUMIF
```

### Identificadores de columna
- `[GrossLbs]`, `[NetLbs]`, `[TareLbs]`, `[Pieces]`, `[ChargeableKg]`, `[MaxPayloadKg]`...
- El backend expone un **catálogo de campos disponibles** (`GET /api/dashboard-builder/fields`) con su tipo, unidad y fórmula por defecto (proveniente de la lógica real existente).

### Funciones de negocio (registradas en el motor, NO código libre)
| Función | Fórmula de ejemplo | Lógica real del backend |
|---------|--------------------|--------------------------|
| `TareOf(tipo)` | `TareOf('PMC')` | config de tara por tipo ULD |
| `LbsToKg(x)` | `LbsToKg([GrossLbs])` | × 0.453592 (o /2.20462) |
| `ChargeableKg(len,wid,hei,pcs)` | `ChargeableKg(50,40,30,5)` | dim weight vol/366 (coincide con recibo) |
| `Utilization(netKg, maxPayloadKg)` | `Utilization([NetLbs],[MaxPayloadKg])` | netKg/maxPayloadKg |
| `Projection(val, rate)` | `Projection([NetLbs], 0.10)` | val × (1+rate) — escenarios |
| `YOY(Ganancia anterior)` | funcionalidad agregada de contextos | resta/división |

### Reglas de seguridad (clave para hacerlo "acabado")
- **Allowlist**: solo se permiten campos del catálogo + funciones registradas. Sin acceso a objetos/métodos arbitrarios.
- **Sandbox**: exp4j/Fórmula.js evalúan en un contexto cerrado; sin IO, sin red, sin acceso a memoria global.
- **Tope de cómputo**: límite de filas evaluadas (p.ej. 5.000) y de profundidad/iteraciones para evitar fórmulas que cuelgan el navegador o el servicio.
- **Validación en el frontend** con preview en vivo (segundo a segundo) y **validación final en backend** antes de persistir.

---

## 4. Escenarios / Proyecciones

El usuario puede **sobreescribir parámetros** para recrear escenarios sin tocar la BD:
- Definir un **"escenario"** como lista de pares `variable=valor` (p.ej. `tasaCrecimiento=0.10`, `tareNueva=150`, `maxPayload=PX`).
- Las fórmulas pueden referenciar estas variables: `Projection([NetLbs], [tasaCrecimiento])`.
- Guardar escenarios por nombre y comparar (barras/curvas de "real vs proyectado").

### Motor de agrupación (pivot simple)
- Columnas de **dimensión**: `Vuelo`, `Destino`, `TipoULD`, `Commodity`, `Fecha(mes)`.
- Columnas de **medida**: campos numéricos + fórmulas calculadas.
- El backend agrupa/agrega (SUM/AVG por grupo) devolviendo filas listas para el gráfico.

---

## 5. Gráficos (Chart.js u otras opciones)

| Herramienta | Tipo | Nota |
|-------------|------|------|
| **Chart.js + vue-chartjs** | Barras, línea, dona, scatter, radar | **Recomendado**: ligera, canvas, ya es el estándar, bueno con Vue 3. Apta para series/pivots/donas por commodity (coincide con la paleta de colores existente del Dashboard). |
| **ECharts (apache/echarts)** | Mucho más potente (geo, heatmaps, 3D) | Si más adelante se quieren mapas por ubicación (SDQ/MIA...). Más pesado (~1MB). |
| **ApexCharts + vue3-apexcharts** | Panel moderno, SVG, interactivo | Buena alternativa; algo más pesado que Chart.js. |
| **SVG nativo / CSS puro** | Barras, líneas simples | Cero dependencias, coherente con el estilo mono de la app; suficiente para MVP. |

**Recomendación progresiva**: **MVP = SVG/CSS puro** (barras + línea) para los gráficos de escenario, y añadir **Chart.js** cuando haya más tipos de gráfico (dona por commodity, timeline, radar de ULD por vuelo).

### Cómo se conecta el gráfico
1. El reporte se calcula (backend) → lista de filas + agregados.
2. Se selecciona el gráfico: `tipo` (bar/line/dona), `ejeX` (dimensión), `serieY` (medida calculada).
3. `GET /api/dashboard-builder/chart?config=...` → devuelve `{ labels, datasets }` ya calculados, o el frontend los deriva de los datos del reporte con Chart.js data-builder.

---

## 6. Persistencia

### Tabla (nueva migración Flyway en export-service)
```sql
CREATE TABLE dashboard_report (
  id            UUID PK,
  user_id       UUID NOT NULL,            -- dueño
  name          VARCHAR(120) NOT NULL,
  field_sources JSONB NOT NULL,           -- columnas de datos a cargar
  formulas      JSONB NOT NULL,           -- [{ col, expr }] campos calculados
  scenario      JSONB,                    -- [{ variable, value }] de proyección
  grouping      JSONB,                    -- dimensión(es) + medidas
  chart_config  JSONB,                    -- { type, xAxis, series } (Chart.js)
  is_shared     BOOL DEFAULT false,
  created_at    TIMESTAMPTZ,
  updated_at    TIMESTAMPTZ
);
```
- **Endpoints** (ADMIN/OPERATIONS/TRAFFIC/SUPER_USER mutan; resto lee):
  - `GET  /api/dashboard-builder/fields` — catálogo de campos + unidades.
  - `GET /api/dashboard-reports` y `GET /api/dashboard-reports/{id}` — listar/ver.
  - `POST/PUT/DELETE /api/dashboard-reports/...` — CRUD + evaluar/persistir.
  - `POST /api/dashboard-reports/evaluate` — body `{ reportConfig }` → `{ rows, totals }` (motor exp4j).
  - `GET  /api/dashboard-reports/{id}/chart` — datos de serie para Chart.js.

### Frontend
- **`components/dashboard/ReportEditor.vue`**: rejilla con columnas de datos + columna nueva "＋ Campo calculado" que abre el editor de fórmula (con autocompletado del catálogo de campos + función).
- **`components/dashboard/FormulaInput.vue`**: input con preview en vivo (Formula.js) y menú de funciones.
- **`components/dashboard/ScenarioBar.vue`**: variables de proyección (sliders/inputs) que re-evalúan en vivo.
- **`components/dashboard/ReportChart.vue`**: envuelve Chart.js o SVG según el tipo.
- **`stores/dashboardReports.js` + `api/dashboardReports.js`**.
- **`views/DashboardView.vue`**: nuevo tab **"Dashboard Builder"** que contiene los sub-tabs `widgets` (drag&drop) y `reports` (reporte calculable). Solo botón de edición para los 4 roles; resto lee.

---

## 7. Catálogo inicial de campos calculados habituales (basados en lógica real existente)
| Campo calculado | Fórmula (DSL) | Backend |
|-----------------|----------------|---------|
| Net weight (lbs) | `[GrossLbs] - TareOf([UldType])` | coincide con `BiService` net = gross - tare; `Uld` entity |
| Net weight (kg) | `LbsToKg([NetLbs])` | × 0.453592 |
| Payload utilización | `Utilization([NetKg], [MaxPayloadKg])` | `flight-performance` |
| Chargeable (dim wt) | `ChargeableKg([L],[W],[H]) * [Pieces]` | coincide con recibo (vol/366, lbs vol/194) |
| Incremento proyectado | `Projection([NetLbs], [tasaCrecimiento])` | escenario |
| Sobra de capacidad | `[MaxPayloadKg] - [NetKg]` | flight-performance |
| Peso por pieza | `[GrossLbs] / [Pieces]` | aritmética |
| Ratio dispatch | `[DispatchedPcs] / [ReceivedPcs]` | mawb-matrix |

---

## 8. Roadmap sugerido
1. **P0 — Motor de campos calculados (backend)**: tabla `dashboard_report` + `GET /fields` + motor exp4j con funciones de negocio + `POST /evaluate`. Tests unitarios (fórmula válida, división por cero → null, allowlist, tope de filas).
2. **P0 — Editor de reporte (frontend)**: `ReportEditor.vue` + `FormulaInput.vue` con preview Formula.js + rejilla de datos y columnas calculadas (escenario básico por variables).
3. **P1 — Gráficos**: `ReportChart.vue` con SVG/Chart.js (barras de proyección, dona por commodity), datos del backend.
4. **P1 — Guardado/roles**: endpoints CRUD + `@PreAuthorize` de los 4 roles + `is_shared`.
5. **P2 — Comparación de escenarios y export** (XLSX/CSV del reporte, siguiendo el patrón de `WeightReportService` / `ExportService`).

> Nota de costo: Formula.js + Chart.js son dependencias nuevas del frontend; exp4j es una dependencia nueva pequeña del backend. Ambas son estándar y mantenidas. Ninguna compromete la compilación offline si se resuelven una vez (`dependency:resolve`).
