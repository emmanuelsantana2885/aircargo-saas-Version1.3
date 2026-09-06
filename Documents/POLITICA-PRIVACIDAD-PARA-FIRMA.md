# POLÍTICA DE PRIVACIDAD Y PROTECCIÓN DE DATOS PERSONALES

**Aircargo SaaS — Versión 1.3**  
**Fecha de vigencia:** [FECHA_VIGENCIA]  
**Versión del documento:** 1.0  
**Clasificación:** Público  

---

## 1. IDENTIFICACIÓN DEL RESPONSABLE DEL TRATAMIENTO

| Campo | Información |
|-------|-------------|
| **Denominación social** | [RAZÓN_SOCIAL_COMPLETA] |
| **Nombre comercial** | [NOMBRE_COMERCIAL] |
| **RNC** | [RNC_EMPRESA] |
| **Domicilio social** | [DIRECCIÓN_COMPLETA_DOMICILIO_SOCIAL] |
| **Ciudad / Provincia** | [CIUDAD_PROVINCIA] |
| **País** | República Dominicana |
| **Correo electrónico DPO / Contacto privacidad** | [EMAIL_DPO_PRIVACIDAD] |
| **Teléfono de contacto** | [TELEFONO_CONTACTO] |
| **Sitio web** | [URL_SITIO_WEB] |

**Representante legal:** [NOMBRE_REPRESENTANTE_LEGAL]  
**Cargo:** [CARGO_REPRESENTANTE]  
**Documento de identidad:** [TIPO_DOC_IDENTIDAD] No. [NUMERO_DOC_IDENTIDAD]  

---

## 2. ALCANCE Y FINALIDAD

La presente Política de Privacidad regula el tratamiento de datos personales que realiza **[RAZÓN_SOCIAL_COMPLETA]** (en adelante, **"el Responsable"**, **"nosotros"** o **"la Empresa"**) en el marco de la prestación del servicio **Aircargo SaaS** (en adelante, **"la Plataforma"**), plataforma de gestión de carga aérea que incluye módulos de reservas, manifiestos, planificación de carga, almacén, ULDs, documentación de cumplimiento (DUA), notificaciones y analítica.

**Finalidades del tratamiento:**
1. **Gestión contractual y operativa:** Ejecución de contratos de transporte aéreo, emisión de documentos (MAWB/HAWB), gestión de reservas, planificación de carga, recepción en almacén, control de ULDs.
2. **Cumplimiento legal y regulatorio:** Obligaciones fiscales (DGII), aduaneras (DGA), aeronáuticas (JAC/IATA), laborales y de seguridad social.
3. **Seguridad de la información:** Autenticación, autorización, auditoría, prevención de fraude, gestión de incidentes.
4. **Comunicaciones operativas:** Notificaciones de estado de envíos, alertas operativas, recordatorios de vencimientos.
5. **Mejora del servicio:** Analítica agregada, métricas de rendimiento, optimización de rutas y capacidad.
6. **Ejercicio de derechos:** Gestión de solicitudes ARCO y hábeas data.

**Base legal:** Art. 5 Ley 172-13 (consentimiento, ejecución contractual, obligación legal, interés legítimo, interés vital).

---

## 3. CATEGORÍAS DE DATOS PERSONALES TRATADOS

| Categoría | Datos incluidos | Finalidad principal | Base legal |
|-----------|-----------------|---------------------|------------|
| **Datos de identificación** | Nombre, apellido, cédula/RNC, pasaporte, foto documento, firma | Identificación de usuarios, firmantes, consignatarios | Ejecución contractual, obligación legal |
| **Datos de contacto** | Email, teléfono, dirección física, sitio web | Comunicaciones operativas, notificaciones | Ejecución contractual, interés legítimo |
| **Datos profesionales** | Cargo, empresa, departamento, permisos/roles | Control de acceso (RBAC), auditoría | Interés legítimo, obligación legal |
| **Datos de autenticación** | Email (usuario), hash contraseña (BCrypt), secreto MFA (TOTP cifrado AES-256-GCM), tokens JWT, tokens de refresco | Seguridad, control de acceso, sesión | Interés legítimo, consentimiento |
| **Datos de auditoría** | IP (pseudonimizada: último octeto truncado), user-agent, acción, entidad, timestamp, resultado | Trazabilidad, cumplimiento, investigación incidentes | Interés legítimo, obligación legal |
| **Datos operacionales** | Reservas, MAWB/HAWB, piezas, pesos, dimensiones, mercancía, origen/destino, vuelo, ULD, almacén | Ejecución del servicio de carga aérea | Ejecución contractual, obligación legal |
| **Datos de cumplimiento (DUA)** | Declaraciones, estados, observaciones, responsables, fechas | Cumplimiento aduanero (DGA) | Obligación legal |
| **Metadatos técnicos** | Logs de aplicación, métricas de rendimiento, errores, trazas distribuidas | Operación, depuración, mejora continua | Interés legítimo |

**Datos NO tratados:** Datos sensibles (origen étnico, salud, orientación sexual, convicciones religiosas, afiliación sindical, datos biométricos con fines de identificación única), datos de menores de edad.

---

## 4. DESTINATARIOS Y TRANSFERENCIAS

### 4.1 Destinatarios internos
- Personal autorizado de la Empresa según principio de necesidad de conocer (need-to-know).
- Roles: Administradores, Operaciones, Tráfico, Planificadores de carga, Almacén, Solo lectura.

### 4.2 Encargados del tratamiento (Procesadores)
| Encargado | Servicio | Ubicación | Garantías |
|-----------|----------|-----------|-----------|
| **Amazon Web Services / Google Cloud / Azure** | Infraestructura cloud (compute, storage, DB, MQ) | [REGIÓN_CLOUD] | Cláusulas contractuales tipo (SCC), certificación ISO 27001, SOC 2 |
| **Proveedor PostgreSQL gestionado** | Base de datos principal | [REGIÓN_DB] | Cifrado en tránsito (TLS 1.2+), en reposo (AES-256), backup cifrado |
| **Proveedor RabbitMQ gestionado** | Mensajería eventos | [REGIÓN_MQ] | TLS mutuo, aislamiento de red |
| **Proveedor Redis gestionado** | Caché y rate limiting | [REGIÓN_REDIS] | TLS, ACL, cifrado en reposo |
| **Proveedor SMTP transaccional** | Envío emails (MFA, notificaciones, alertas) | [PROVEEDOR_SMTP] | TLS, SPF/DKIM/DMARC configurados |
| **Cert-manager / Let's Encrypt** | Certificados TLS | Global | ACME, validación de dominio |

### 4.3 Transferencias internacionales
**No se realizan transferencias internacionales de datos personales** fuera de los encargados listados arriba, quienes operan bajo cláusulas contractuales tipo aprobadas y garantías adecuadas (Art. 24 Ley 172-13). Los datos residen en [REGIÓN_CLOUD] y no se replican a otras jurisdicciones sin autorización expresa.

### 4.4 Autoridades y terceros por obligación legal
- Dirección General de Impuestos Internos (DGII)
- Dirección General de Aduanas (DGA)
- Junta de Aviación Civil (JAC)
- Instituto Dominicano de Aviación Civil (IDAC)
- Ministerio de Trabajo
- Tribunal competente mediante orden judicial

---

## 5. MEDIDAS DE SEGURIDAD TÉCNICAS Y ORGANIZATIVAS

### 5.1 Seguridad técnica implementada
| Medida | Descripción | Estado |
|--------|-------------|--------|
| **Cifrado en tránsito** | TLS 1.2+ obligatorio en todas las comunicaciones (ingress, inter-servicios, DB, MQ, Redis, SMTP) | ✅ Implementado |
| **Cifrado en reposo** | AES-256-GCM para secretos sensibles (MFA TOTP, cédulas, firmas, docs ID); PostgreSQL TDE; backups cifrados | ✅ Implementado |
| **Autenticación fuerte** | MFA obligatorio (TOTP RFC 6238), re-enrolamiento tras reinicio/política, bloqueo 5 intentos fallidos (30 min) | ✅ Implementado |
| **Control de acceso** | RBAC 7 roles, principio menor privilegio, JWT HS512 (rotación 1h access / 7d refresh), revocación central (`tokens_valid_from`) | ✅ Implementado |
| **Auditoría inmutable** | Event sourcing append-only (`audit_event`), retención 24 meses, purge automático, pseudonimización IP | ✅ Implementado |
| **Seguridad perimetral** | WAF (nginx), rate limiting (Redis compartido HA), circuit breaker, CSP, HSTS, headers seguridad | ✅ Implementado |
| **Seguridad runtime** | Kyverno (Pod Security Restricted), Falco (13 reglas custom), contenedores non-root, read-only FS, drop ALL capabilities | ✅ Implementado |
| **Gestión de vulnerabilidades** | Trivy (FS, imagen, secretos), OWASP Dependency Check, SBOM CycloneDX, Cosign signing | ✅ Implementado |
| **Backup y recuperación** | Backup diario automático (2 AM UTC), offsite (S3), retención 30 días, restore verificado, auto-restore ante fallo deploy | ✅ Implementado |
| **Observabilidad** | Logs centralizados (Loki+Vector), trazas distribuidas (Tempo+OTEL), métricas (Prometheus), alertas (40+ reglas) | ✅ Implementado |

### 5.2 Seguridad organizativa
- **Políticas documentadas:** Política de seguridad de la información, política de control de acceso, política de respuesta a incidentes, política de retención y disposición.
- **Formación:** Capacitación anual en privacidad y seguridad para todo el personal con acceso a datos.
- **Acuerdos de confidencialidad:** Todo el personal y encargados firman NDA/cláusulas de confidencialidad.
- **Evaluación de riesgos:** Análisis de riesgos (DPIA) realizado para tratamientos de alto riesgo; revisión anual.
- **Plan de respuesta a incidentes:** Procedimiento definido (detección, contención, erradicación, recuperación, lecciones aprendidas), notificación a autoridad en 72h si procede (Art. 15 Ley 172-13).

---

## 6. PLAZOS DE CONSERVACIÓN

| Categoría de datos | Plazo de conservación | Disposición final |
|---------------------|----------------------|-------------------|
| **Datos contractuales/operacionales** | 10 años desde fin relación contractual (Art. 32 Código Comercio) | Eliminación segura / anonimización |
| **Datos fiscales/contables** | 10 años (Código Tributario, Art. 68) | Eliminación segura |
| **Datos aduaneros (DUA)** | 5 años desde despacho (Ley 168-21 Aduanas) | Eliminación segura |
| **Auditoría y seguridad** | 24 meses (Política interna + Art. 14 Ley 172-13) | Purga automática mensual |
| **Autenticación y sesión** | Tokens: 1h access / 7d refresh; Logs login: 24 meses | Purga automática |
| **Backups** | 30 días en S3 (diarios), 12 meses (mensuales), 7 años (anuales) | Eliminación segura |
| **Datos de marketing/comunicaciones** | Hasta revocación consentimiento | Eliminación inmediata |

**Criterio general:** Los datos se conservan el tiempo estrictamente necesario para la finalidad para la que fueron recabados y mientras existan obligaciones legales de conservación.

---

## 7. DERECHOS DE LOS TITULARES (ARCO + HÁBEAS DATA)

De conformidad con los Arts. 6, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127, 128, 129, 130, 131, 132, 133, 134, 135, 136, 137, 138, 139, 140, 141, 142, 143, 144, 145, 146, 147, 148, 149, 150, 151, 152, 153, 154, 155, 156, 157, 158, 159, 160, 161, 162, 163, 164, 165, 166, 167, 168, 169, 170, 171, 172, 173, 174, 175, 176, 177, 178, 179, 180, 181, 182, 183, 184, 183, 184, 185, 186, 187, 188, 189, 190, 191, 192, 193, 194, 195, 196, 197, 198, 199, 200 de la Ley 172-13, los titulares pueden ejercer:

| Derecho | Descripción | Plazo respuesta |
|---------|-------------|-----------------|
| **Acceso** | Conocer qué datos tratamos, finalidad, origen, destinatarios, plazos | 10 días hábiles |
| **Rectificación** | Corregir datos inexactos o incompletos | 10 días hábiles |
| **Cancelación / Supresión** | Eliminar datos cuando ya no sean necesarios, se revoque consentimiento, o tratamiento ilícito | 10 días hábiles |
| **Oposición** | Oponerse al tratamiento por interés legítimo o marketing directo | 10 días hábiles |
| **Limitación** | Restringir tratamiento mientras se verifica exactitud, legalidad, o ejercicio de derechos | 10 días hábiles |
| **Portabilidad** | Recibir datos en formato estructurado, de uso común y lectura mecánica; transmitir a otro responsable | 10 días hábiles |
| **No ser objeto de decisiones automatizadas** | Derecho a intervención humana en decisiones con efectos jurídicos | Inmediato |
| **Hábeas Data** | Acceso a registro de datos personales en bases de datos públicas/privadas | 10 días hábiles |
| **Revocar consentimiento** | Retirar consentimiento en cualquier momento (sin afectar licitud previa) | Inmediato |

### Procedimiento de ejercicio
1. **Canal:** Email a **[EMAIL_DPO_PRIVACIDAD]** con asunto "EJERCICIO DERECHOS ARCO - [NOMBRE TITULAR]"
2. **Requisitos:** Identificación del titular (copia cédula/pasaporte), descripción clara del derecho ejercido, datos específicos afectados.
3. **Representación:** Si actúa mediante apoderado, poder notarial o documento privado con firma certificada.
4. **Respuesta:** Por el mismo medio o el indicado por el titular. Si no se atiende en plazo, puede recurrir a la autoridad competente.

---

## 8. AUTORIDAD DE CONTROL Y RECURSOS

**Autoridad competente en República Dominicana:**  
Actualmente la **Procuraduría General de la República** (a través de la Dirección Nacional de Protección de Datos Personales en proceso de conformación conforme Art. 46 Ley 172-13) y los **Tribunales de la República** (acción de hábeas data, Art. 44 Constitución).

**Vía judicial:** Acción de hábeas data (Art. 44 Constitución, Ley 172-13 Arts. 44-45) ante juez de la jurisdicción del domicilio del titular o donde se encuentren los datos.

---

## 9. MODIFICACIONES DE LA POLÍTICA

Cualquier modificación sustancial será notificada a los titulares con **30 días de antelación** mediante:
- Publicación en la Plataforma (banner visible)
- Email a usuarios registrados
- Registro de versión en este documento

Las modificaciones no retroactivas que reduzcan derechos requerirán nuevo consentimiento expreso.

---

## 10. CONTACTO PARA CUESTIONES DE PRIVACIDAD

**Delegado de Protección de Datos (DPO) / Responsable de Privacidad:**  
**Nombre:** [NOMBRE_DPO]  
**Email:** [EMAIL_DPO_PRIVACIDAD]  
**Teléfono:** [TELEFONO_DPO]  
**Dirección postal:** [DIRECCION_POSTAL_DPO]  

---

## 11. ANEXOS

- **Anexo A:** Registro de Actividades de Tratamiento (ROPA) — documento interno.
- **Anexo B:** Evaluación de Impacto en Protección de Datos (DPIA) — documento interno.
- **Anexo C:** Contrato de Encargo de Tratamiento (modelo) — ver documento separado.
- **Anexo D:** Procedimiento de Respuesta a Incidentes de Seguridad — documento interno.
- **Anexo E:** Matriz de Retención y Disposición Documental — documento interno.

---

**FIRMA DEL REPRESENTANTE LEGAL**

En **[CIUDAD]**, a **[DÍA] de [MES] de [AÑO]**.

__________________________________________
**[NOMBRE_REPRESENTANTE_LEGAL]**  
**[CARGO_REPRESENTANTE]**  
**[RAZÓN_SOCIAL_COMPLETA]**  
**RNC:** [RNC_EMPRESA]  
**Cédula:** [TIPO_DOC_IDENTIDAD] No. [NUMERO_DOC_IDENTIDAD]  

---

*Documento generado automáticamente desde plantilla. Versión controlada en repositorio de configuración.*