# Revisión de Privacidad y Datos Personales — aircargo-saas
**Fecha:** 22 ago 2026 · **Alcance:** inventario de datos personales en el código, controles existentes, comparación con requisitos legales de República Dominicana y EE.UU., brechas y recomendaciones.
> ⚠️ Este documento es un análisis técnico, **no asesoría legal**. Las decisiones de cumplimiento deben validarse con abogado dominicano y/o estadounidense.

---

## 1. Inventario de datos personales en el sistema

| # | Dato | Ubicación | Sensibilidad | Estado actual |
|---|------|-----------|--------------|---------------|
| 1 | Email, nombre completo, rol, aerolínea | `AppUser` (auth-service) | Identificativo | OK funcional |
| 2 | Hash de contraseña | `AppUser.passwordHash` | Credencial | ✅ BCrypt centralizado (bean único) |
| 3 | Secreto TOTP (MFA) | `AppUser.mfaSecret` (AppUser.java:52) | **Credencial** | ❌ Plano en BD, sin cifrar |
| 4 | IP address de cada acción | `audit_event.ipAddress`, `AuditService` (login, lockout, todas las acciones) | Identificativo (pseudónimo) | ⚠️ Retención indefinida |
| 5 | Email/nombre en auditoría | `audit_event` (email, fullName, payload JSON) | Identificativo | ⚠️ Retención indefinida |
| 6 | **Cédula/ID de quien recibe y del broker** | `WarehouseReceipt.deliveredByIdNum`, `brokerIdNum` (warehouse-service) | **Identificador gubernamental** | ❌ Plano en BD, aparece en PDFs |
| 7 | Nombres receptor/emisor/broker | `WarehouseReceipt.deliveredByName/receivedByName/brokerName/printName` | Identificativo | ⚠️ Plano (normal para operación) |
| 8 | **Firma manuscrita** | `WarehouseReceipt.dockSignature` (TEXT, imagen base64) | **Datos biométricos-light / identificador fuerte** | ❌ Sin cifrar |
| 9 | Contraseña temporal | generada al crear usuario (`AppUserController.java:206`) | Credencial | ❌ **Se envía en claro por email** |
| 10 | Intentos fallidos + bloqueo | `failedLoginAttempts`, `lockedUntil` | Identificativo | ✅ Límite 5, lockout 30 min |
| 11 | Sesiones activas (heartbeat) | `ActiveSessionTracker` | Identificativo | ✅ Purga automática (post-fix @EnableScheduling) |

**Terceros externos:** ninguno en runtime (PostgreSQL/RabbitMQ locales; SMTP propio). Sin analytics, tracking, CDNs de terceros ni SDKs en el frontend (verificado: cero coincidencias gtag/sentry/analytics).
**IA:** la aplicación **no incorpora funciones de IA** en runtime (ninguna llamada a LLM desde backend/frontend). La IA solo participó como herramienta de desarrollo (opencode/Claude) y Graphify corre en modo local `--code-only`.

## 2. Controles de seguridad ya implementados (positivos)

- ✅ BCrypt en todas las contraseñas (bean compartido `PasswordEncoderConfig`)
- ✅ Política fuerte de contraseña: 12+ chars, mayús/minús/dígito/especial (`@StrongPassword`)
- ✅ Bloqueo tras 5 intentos fallidos + mensaje genérico "Email y/o contraseña incorrectos" (no revela existencia de cuentas)
- ✅ MFA TOTP opcional por usuario
- ✅ Validación `@Email`, `@NotBlank`, `@Valid` en DTOs
- ✅ Auditoría append-only event-sourced (integridad, trazabilidad completa)
- ✅ Rate-limit conceptual vía lockout; JWT con expiración + refresh tokens
- ✅ `secret.yml` movido fuera del repo y `.gitignore` protege `k8s/secret*.yml`

## 3. Brechas principales encontradas

1. **No existe política/aviso de privacidad** — cero menciones a privacidad, consentimiento o tratamiento de datos en frontend ni backend (búsqueda exhaustiva: 0 resultados).
2. **Credenciales en tránsito**: contraseña temporal en texto plano por SMTP sin autenticación ni STARTTLS (`notification-service/application.properties:38-39` → `smtp.auth=false`, `starttls.enable=false`). Un intermediario puede leerla.
3. **`mfaSecret` sin cifrar en reposo**: quien obtenga la BD puede generar códigos TOTP de cualquier usuario (bypass total de MFA).
4. **Cédulas + firmas manuscritas sin protección especial**: datos altamente identificativos en texto plano e imágenes base64; exposición amplia (PDFs descargables).
5. **Retención indefinida**: auditoría conserva email/nombre/IP para siempre; no hay política de conservación ni anonimización al borrar usuarios (`deleteById` físico, la auditoría queda con PII eterna).
6. **JWT_SECRET tiene default inseguro embebido** (`dev-only-insecure-secret-do-not-use-in-production-please-change-me`) — si alguien despliega sin definir la variable, cualquiera falsifica tokens.
7. Sin HTTPS configurado a nivel aplicación (presumiblemente termina TLS en nginx/ingress — verificar certificado en producción).
8. Sin registro de actividades de tratamiento ni evaluación de impacto.

---

## 4. Comparación legal

### 4.1 República Dominicana

| Requisito (Ley 172-13 y afines) | ¿Aplica? | Estado del proyecto |
|---|---|---|
| **Consentimiento libre, expreso y consciente** (Art. 5) | Sí — para datos de terceros (receptores/brokers de carga); para empleados opera la relación laboral como base práctica | ❌ No se captura ni documenta |
| **Deber de información** (Art. 6): finalidad, responsable, destino, derechos | Sí | ❌ Sin aviso de privacidad en la app |
| **Derechos ARCO + acceso/rectificación** con respuesta ≤ 10 días; habeas data (Constitución Art. 44) | Sí | ⚠️ Parcial: admin puede editar/borrar usuarios, pero el titular no tiene canal formal |
| **Seguridad de los datos** (Arts. 14-15): medidas técnicas acordes al riesgo | Sí | ⚠️ Buena base (BCrypt, lockout, auditoría) pero fallan puntos 2-4 de brechas |
| **Secreto profesional / confidencialidad** (Art. 18) | Sí | ⚠️ No documentado (NDA/DPA con desarrollador) |
| **Registro de bancos de datos** / documentación del tratamiento | Formalmente sí | ❌ No existe |
| **Notificación de daños por violación de seguridad** (Art. 16: notificar al titular) | Sí | ❌ Sin plan de respuesta a incidentes |
| **Datos sensibles** (salud, ideología, biometría…) con régimen reforzado (Art. 7) | Discutible — la firma manuscrita y cédula no son "sensibles" técnicos, pero exigen cuidado reforzado | ❌ Sin protección especial |
| **Autoridad de control** | *Nota:* la ley existe desde 2013 pero su autoridad de supervisión nunca fue plenamente operativa; hay Anteproyecto de reforma (minimización, limitación de conservación, autoridad nacional) en la Agenda Digital 2030 | — Cumplir "por encima" del mínimo: la reforma apunta justo a las brechas 3-5 |

**Leyes complementarias RD:** Ley 53-07 (delitos informáticos: interceptación/acceso ilegítimo — penaliza filtraciones por mala praxis); Ley 155-17 (ciberdelitos); Código Penal. La app es B2B (empleados de aerolíneas/agentes), lo que reduce exposición frente a "consumidores", pero los datos de **terceros receptores de carga (cédulas, firmas)** son tratamiento de datos personales de personas físicas igualmente.

### 4.2 Estados Unidos

| Requisito | ¿Aplica? | Estado del proyecto |
|---|---|---|
| **Leyes estatales integrales** (CA CPRA, VA, CO, CT, UT, TX, OR, MT, DE, IA, NE, NH, NJ, TN, MN, MD… ~19-20 vigentes 2026) | Solo si procesa datos de residentes y supera umbrales (típicamente 100k consumidores o venta de datos). App interna B2B normalmente queda **fuera por umbral**, PERO: **CPRA de California cubre datos de empleados y contactos B2B sin umbral de volumen desde 2023** — si hay personal en CA, aplica notice + derechos | ❌ Sin notice; ⚠️ derechos administrables manualmente |
| **FTC Act §5** (prácticas engañosas/injustas): promesas de privacidad deben cumplirse + seguridad razonable | Sí, siempre (estándar de mercado) | ⚠️ Seguridad razonable: mayormente OK, brechas 2-3-6 serían difíciles de defender |
| **Leyes de notificación de brechas** (50 estados + DC, plazos 30-60 días) | Si hay datos de residentes de cualquier estado | ❌ Sin plan de respuesta |
| **Contratos con processors (DPA)** exigidos por todas las leyes estatales | El estudio de software es processor/subprocessor | ❌ Sin DPA firmado |
| **Colorado AI Act (SB 24-205)** — high-risk AI | Vigencia 30-jun-2026 (retrasada; enforcement pausado por corte federal el 27-abr-2026, posible reemplazo). Solo aplica a IA que toma decisiones consecuentes (empleo, crédito…) | ✅ **Fuera de alcance**: la app no usa IA en runtime |
| **Texas TRAIGA** (vigente 1-ene-2026) — prohibiciones puntuales de IA | Solo usos prohibidos (social scoring, deepfakes nocivos) | ✅ Fuera de alcance |
| **Utah AI Policy Act** (vigente may-2024) — divulgar gen-AI en profesiones reguladas / al preguntar | Solo si un bot de IA conversacional atiende usuarios | ✅ Fuera de alcance |
| **California AB 2013 / SB 53 / SB 942** (2026) — transparencia de modelos generativos | Solo proveedores/frontier models | ✅ Fuera de alcance |
| **Sectorial carga aérea** (TSA/CBP: ACAS, eAWB, known shipper) | Si la app alimenta operaciones hacia/desde EE.UU. | ⚠️ Compliance aduanero separado, no privacidad per se |

### 4.3 Aplicaciones construidas con IA — el punto clave solicitado

1. **Ninguna ley de RD ni de EE.UU. exige revelar que el código fue escrito con asistencia de IA.** Las obligaciones de divulgación de IA (Utah, Colorado, Texas, California) aplican a *sistemas de IA* que interactúan con personas o toman decisiones sobre ellas — no a la herramienta con la que se programó. Esta app no incrusta IA → no entra en ninguna categoría regulada de IA hoy.
2. **Riesgo real #1 — confidencialidad**: durante el desarrollo se envió código a LLMs externos (Claude/opencode). Verificar que la suscripción sea plan Business/Team/API con **no-training guarantee**, y nunca pegar datos de producción, credenciales ni secretos en prompts. (Buenas noticias: `secret.yml` salió del repo y no contiene valores reales, solo placeholders.)
3. **Riesgo real #2 — Graphify semántico**: el modo completo envía documentación a APIs de LLM (Gemini/OpenAI/etc.). Mantener `--code-only` (estado actual, 100% local) o revisar qué archivos indexa antes de habilitarlo.
4. **Propiedad intelectual**: los términos de Anthropic/Google asignan la salida al usuario; documentar en contrato con el cliente quién es dueño del código entregado (cláusula estándar en desarrollos a medida).
5. Si en el futuro se agregan funciones de IA a la app (chatbots, scoring automático), recién ahí entran Utah AIPA (divulgación), Colorado (evaluaciones de impacto) y las reglas de decisiones automatizadas.

### 4.4 Tabla resumen de brechas vs. jurisdicción

| Brecha | RD 172-13 | EE.UU. (estatal + FTC) | Prioridad |
|---|---|---|---|
| Sin política de privacidad | Art. 6 ❌ | Notice state laws + FTC ❌ | 🔴 Alta |
| TempPassword por SMTP plano | Art. 14 ❌ | Seguridad razonable FTC ❌ | 🔴 Alta |
| MFA secret sin cifrar | Art. 14 ❌ | Seguridad razonable FTC ❌ | 🔴 Alta |
| Cédulas/firmas sin protección | Arts. 14-15 ❌ | CPRA sensitive ⚠️ | 🟠 Media-alta |
| Retención indefinida auditoría | Principio de conservación (Anteproyecto) ⚠️ | Minimización estatal ⚠️ | 🟠 Media |
| JWT default inseguro | Art. 14 ❌ | FTC ❌ | 🔴 Alta (deploy) |
| Sin DPA desarrollador-cliente | Art. 20 ❌ | Exigido por todas las state laws ❌ | 🟠 Media |
| Sin plan de brechas | Art. 16 ❌ | 50 estados ❌ | 🟠 Media |

## 5. Recomendaciones priorizadas

**🔴 Inmediatas (antes de producción):**
1. Forzar `JWT_SECRET` real (fallar el arranque si falta: quitar el default).
2. SMTP con autenticación + STARTTLS/TLS, y **eliminar envío de contraseña temporal**: enviar link de reset de un solo uso con expiración (15 min) en su lugar.
3. Cifrar `mfaSecret` (AES-256-GCM con clave en variable de entorno/K8s Secret, no en la BD).
4. Verificar TLS en ingress/nginx con certificado válido (HSTS).

**🟠 Corto plazo:**
5. Publicar política/aviso de privacidad (página in-app + PDF): responsable del tratamiento, finalidades (operación logística, seguridad, facturación), datos recogidos, derechos ARCO, contacto, retención, transferencias (ninguna).
6. Firmar **contrato de encargo de tratamiento (DPA)** entre el operador (responsable) y el estudio (encargado): instrucciones, confidencialidad, seguridad, subencargados (herramientas IA), borrado al terminar.
7. Política de retención: purgar o anonimizar eventos de auditoría > 24 meses (conservar userId pseudonimizado + acción + timestamp); truncar IP (último octeto) al escribir nuevos eventos.
8. Evaluar cifrado en reposo de `dockSignature` y `*IdNum` (column-level encryption o al menos disco cifrado en el host K8s).

**🟢 Proceso continuo:**
9. Registro interno de actividades de tratamiento (tabla simple: finalidad, datos, base, retención).
10. Plan de respuesta a brechas (detección → contención → notificación a titulares ≤ 10 días RD / 30-60 días según estados).
11. Documentar el stack de desarrollo con IA en el contrato (transparencia comercial, no obligación legal) y cláusula de propiedad intelectual del código entregado.
12. Si algún día hay empleados/contactos en California: notice estilo CCPA para esos datos.

---
*Método: búsqueda exhaustiva en backend (7 servicios Java) y frontend (Vue) de términos de privacidad/consentimiento/cédulas/firmas/IP/tracking; revisión de entidades `AppUser`, `WarehouseReceipt`, `AuditEvent/AuditLog`; configuración de mail/JWT/TLS. Fuentes legales: texto oficial Ley 172-13 (G.O. 10737, 2013), análisis ECIJA/P&H/pucmm 2020-2025, trackers estatales EE.UU. jun 2026 (Colorado SB 25B-004 delay + pausa federal, Texas TRAIGA, Utah SB 149, California AB 2013/SB 53/SB 942).*
