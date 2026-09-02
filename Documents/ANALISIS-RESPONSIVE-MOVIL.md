# Análisis — App 100% Responsive (incl. móvil < 6" / ~360–480 px)

> Auditoría 2026-09-01 sobre los **39 SFC** + `main.css` + layout. La app ya tiene una base responsive
> (sidebar móvil, header wrap, media queries en `main.css`, touch-friendly). Este documento identifica
> **qué falta** para que funcione bien en pantallas pequeñas (~360–480 px, teléfonos <6") y en el rango
> intermedio (tablet / desktop encojido), con recomendaciones concretas por componente.

---

## 1. Diagnóstico del estado actual

### Ya existe (base buena)
- `App.vue`: layout `flex h-screen` con `<main class="flex-1 overflow-auto">` → scroll centralizado (bien).
- `Sidebar.vue`: modo móvil con overlay (`mobileOpen`, backdrop `lg:hidden`), drawer off-canvas. ✅
- `Header.vue`: `flex-wrap`, hamburguesa móvil, oculta `ChevronRight` en `<sm`. ✅
- `main.css`: media queries a 767/1023/640 px, touch targets ≥36px, `font-size:16px` en inputs móviles (evita zoom iOS), tabla con `-webkit-overflow-scrolling`. ✅
- Varias vistas usan `sticky` columns + `minWidth` horizontal scroll para tablas anchas (Dashboard, Mawbs). ✅

### Diagnóstico por breakpoint real
- **Desktop (≥1024 px)**: OK.
- **Tablet / desktop estrecho (768–1023 px)**: la mayoría de tablas anchas **no caben** y dependen de scroll horizontal; falta modo "tarjetas" para las más críticas.
- **Móvil (≤767 px) / <6" (360–480 px)**: la base de scroll horizontal **no escala**: columnas `sticky` consumen ~230px de ancho (ver Dashboard) dejando casi nada del contenido visible → el usuario ve solo 2–3 columnas y el resto es scroll. No hay modo compacto por tarjeta para las vistas principales.

---

## 2. Problemas concretos detectados

### A. Dashboard (`DashboardView.vue`) — el peor en móvil
- `tableMinWidth = 1100 + n*80` (línea 523). Con solo `minWidth:1100px` en una pantalla de 360 px, la tabla ocupa 1100px de scroll horizontal. Las columnas `sticky left-0/left-8/left-16/left-[140px]/left-[220px]` fijan 230px de columnas "pegadas" — en móvil eso es **~64% de la pantalla fija** y deja el resto invisible.
- El header de acciones (`flightsCount`, botón `↓ Descargar`) usa `flex-wrap` pero con `flex-wrap` sí envuelve — OK, aunque el botón queda pequeño.
- No existe vista compacta (tarjetas por vuelo) para móvil.

### B. Tablas anchas heredadas (`MawbsView` 1656 L, `WarehouseReceiptsView` 2640 L, `LoadPlanningView`, `UldsView`, `BookingsView`)
- Uso masivo de `text-[10px]`, `whitespace-nowrap`, `w-14`, `sticky` columns y `minWidth`.
- En <480 px resultan ilegibles (10–11px) y requieren doble scroll (vertical+horizontal).
- El sistema de "tabla" está pensado para desktop; no hay alternativa de **card layout** ni **columnas colapsables**.

### C. Formularios (`WarehouseReceiptsView`, `LoginView`, `SettingsView`, `WarehouseForm`)
- Layouts de 2–3 columnas fijas (`grid-cols-2`, `md:grid-cols-3`) que en móvil a veces quedan a 2 columnas → campos muy estrechos; hay grids 2-col que deberían colapsar a 1 en `<640`.
- Inputs con `min-height:36px` y `font-size:16px` (bien para tap).
- Modales (`ds-modal-panel`) no tienen `max-width: 100%`/`max-h` con scroll en móvil → pueden desbordar verticalmente.

### D. `FilterBar.vue`, `ScanPanel.vue`, `FlightDetail.vue`
- `FilterBar` con muchos controles en una fila → se rompe en `<480px` (usa `flex-wrap` pero con controles grandes acaba apretado).
- `ScanPanel` (input + botones) requiere colapso a pantalla completa en móvil.

### E. Layout global
- `--sidebar-width:230px` cambia a 60px en tablet, pero en móvil el drawer usa `translate-x` (OK).
- `<main>` no tiene `dvh`/`100dvh` → en iOS la barra inferior puede tapar contenido; conviene `min-h-[100dvh]`.

---

## 3. Recomendaciones por prioridad

### P0 — Infraestructura responsable (impacto inmediato en toda la app)
1. **Viewport/altura segura en iOS**: en `main.css` añadir
   ```css
   .app-layout { min-height: 100dvh; }
   ```
2. **Modo tarjeta reutilizable**: crear un componente `ResponsiveTable.vue` que a `sm` (o con un prop `mobile-as-cards`) renderice cada fila como tarjeta apilada (label + valor) y a `lg` renderice la tabla normal. Reusarlo en Dashboard, Mawbs, Ulds, Bookings, LoadPlanning.
3. **Colapsar columnas**: utilidad para ocultar columnas secundarias en móvil (p.ej. columnas marcadas `data-col="hide-sm"` con CSS `@media (max-width:640px){ .hide-sm{display:none} }`). Ya existe `.hide-mobile`; extenderla.
4. **Sidebar**: en móvil ya es drawer; optimizar tocando `min-height:44px` en ítems y `w-80` (320px) máx para no tapar toda la pantalla.

### P1 — Correcciones por vista
| Vista | Acción en móvil |
|-------|-----------------|
| Dashboard | Modo tarjetas por vuelo; quitar `sticky` en `<640px`; totales como chips apilados |
| MawbsView | Cards por MAWB; quitar sticky; tooltips en vez de columnas |
| WarehouseReceiptsView | Formularios a 1 columna; pasos Step en acordeón (ya hay steps → hacerlos colapsables) |
| SettingsView | Tabs a scroll horizontal (`overflow-x-auto`), no wrap amontonado; modales full-width |
| LoadPlanningView / UldsView | Cards; drag&drop táctil con `touch-action: none` + long-press |
| LoginView / MfaSetup / SetPassword | Formularios a 1 columna, botones full-width |
| Modales (`ds-modal-panel`) | `@media (max-width:640px){ .ds-modal-panel{ max-width:100%!important; margin:0!important; border-radius:0; height:100vh; } }` |

### P2 — Mejoras táctiles <6"
- **Menús contextuales y dropdowns**: convertir `hover:` en `click`/`tap` (ya hay patrón, extender).
- **Drag & drop en LoadPlanning**: usar la API Pointer/Pointer Events + `touch-action:none` para que funcione con el dedo (hoy DnD HTML5 no funciona en móvil).
- **Tablas con `position:sticky` horizontal**: en móvil desactivar y usar scroll natural; mantener solo la columna índice.
- **Typo**: en móvil, mínimo `text-[12px]` en datos (hoy 10px es ilegible).

### P3 — Aprovechar filtros en móvil
- `FilterBar` en móvil: modal/desplegable apilado y botón "Filtrar" (evita la fila apretada).

---

## 4. Orden de implementación sugerido

1. **Fase 1 (base)**: `ResponsiveTable.vue` + utilidad `hide-sm` + fix modales + `100dvh`. → cubre el 80% de las vistas a la vez.
2. **Fase 2 (vistas críticas)**: Dashboard (cards) y WarehouseReceiptsView (form 1-col) — las más usadas y las peores en móvil.
3. **Fase 3 (operativas)**: MawbsView, UldsView, LoadPlanning (incl. DnD táctil), SettingsView.
4. **Fase 4 (pulido)**: Login/MFA, ScanPanel, filter-bar móvil, tooltips táctiles.

> Ya existe `Documents/PLAN-VERSION-RESPONSIVA-KMP-10-SEMANAS.xlsx` — este análisis es el desglose técnico que complementa ese plan cronológico con las correcciones por componente concretas.
