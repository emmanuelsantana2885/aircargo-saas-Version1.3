# POLÍTICA DE PRIVACIDAD — Plataforma Aircargo

> **Documento formal** · Versión 1.0 · Agosto 2026
> Marcar los campos `[ENTRE CORCHETES]` antes de publicar. Esta política debe ser adoptada por la empresa operadora del sistema ([EMPRESA_OPERADORA]), que actúa como **Responsable del Tratamiento** conforme a la Ley 172-13 sobre Protección de Datos de Carácter Personal de la República Dominicana.

---

## 1. Responsable del Tratamiento

- **Responsable:** [EMPRESA_OPERADORA], RNC [RNC], con domicilio en [DIRECCIÓN].
- **Contacto del responsable:** [EMAIL_CONTACTO] · [TELÉFONO].
- La operación técnica de la plataforma puede estar a cargo de un **Encargado de Tratamiento** ([PROVEEDOR_DESARROLLO]) bajo contrato de encargo de tratamiento (ver §9).

## 2. Datos tratados

| Categoría | Datos | Origen |
|---|---|---|
| **Usuarios del sistema** (empleados/colaboradores autorizados) | Nombre, correo electrónico, rol, aerolínea, sitios asignados, hash de contraseña, secreto MFA (cifrado), dirección IP, fecha/hora de acceso | Registro por el administrador; uso de la plataforma |
| **Registro de auditoría** | Usuario, acción realizada, entidad afectada, IP, fecha/hora, detalles técnicos | Generado automáticamente por el sistema |
| **Terceros en operaciones de carga** | Nombre, número de documento de identidad (cédula), firma manuscrita digitalizada, fotografía del documento de identidad | Recibos de bodega y comprobantes de entrega |
| **Datos operativos** | Reservas, guías aéreas (MAWB/HAWB), ULDs, vuelos, pesos y dimensiones | Operación logística diaria |

La plataforma **no recopila** datos de navegación publicitaria, no usa cookies de terceros, no incorpora analítica externa ni realiza perfilamiento automatizado de personas.

## 3. Finalidades

1. Operación logística de carga aérea (reservas, recibos, manifiestos, trazabilidad de ULDs).
2. Seguridad de la información: autenticación, control de acceso basado en roles, auditoría de acciones, prevención de accesos no autorizados (bloqueo tras intentos fallidos).
3. Cumplimiento de obligaciones legales, contractuales y aduaneras aplicables al transporte de carga.
4. Contacto operativo con usuarios y terceros relacionados con envíos específicos.

**No se utilizan los datos para publicidad, venta a terceros ni decisiones automatizadas con efectos jurídicos.**

## 4. Base de tratamiento

- Consentimiento libre, expreso e informado del titular (Art. 5, Ley 172-13) para datos de terceros receptores de carga, recabado al momento de la entrega mediante la firma del recibo de bodega.
- Ejecución de relación laboral/contractual para datos de usuarios internos.
- Interés legítimo de seguridad informática para registros de auditoría.

## 5. Conservación

- Datos operativos y recibos: mientras dure la relación comercial + [5] años (recomendación documental/logística).
- Registros de auditoría: [24] meses; transcurrido ese plazo se eliminan o anonimizan.
- Tokens de restablecimiento de contraseña: expiran a los 15 minutos o al primer uso.
- Sesiones activas: purgadas automáticamente a los 5 minutos de inactividad.
> ⚠️ Nota interna: la anonimización automática de auditoría está pendiente de implementación — ver debilidad #8 del análisis de privacidad.

## 6. Seguridad (medidas implementadas)

- Contraseñas almacenadas únicamente como hash BCrypt; nunca en texto plano.
- Campos sensibles (secretos MFA, cédulas, documentos de identidad, firmas) cifrados **AES-256-GCM** en reposo.
- Transporte cifrado TLS terminado en el borde ([nginx/ingress]).
- Control de acceso por roles (RBAC de 8 roles) aplicado en backend y frontend.
- Autenticación de dos factores (TOTP) disponible para todos los usuarios.
- Bloqueo temporal de cuentas tras 5 intentos fallidos y mensajes genéricos que no revelan existencia de cuentas.
- Revocación central de sesiones: bloqueo de cuenta, cambio de contraseña o desactivación invalidan los tokens activos.
- Copias de seguridad diarias automáticas con retención de 30 días.
- Registro íntegro de acciones (event sourcing append-only).

## 7. Derechos del titular (ARCO)

El titular puede ejercer **acceso, actualización, rectificación, cancelación/eliminación y oposición** al tratamiento, así como solicitar información sobre el mismo, dirigiéndose a [EMAIL_CONTACTO]. El responsable responderá en un plazo máximo de **10 días hábiles**. Contra una respuesta insatisfactoria procede la acción de **habeas data** ante el tribunal competente (Constitución, Art. 44).

## 8. Transferencias

Los datos se almacenan en servidores bajo control del responsable ([UBICACIÓN_SERVIDOR]). **No se realizan transferencias internacionales** ni cesiones a terceros, salvo obligación legal.

## 9. Encargado del Tratamiento

El desarrollo/mantenimiento técnico puede ejecutarse bajo contrato de encargo de tratamiento que obliga al encargado a: tratar datos únicamente por instrucciones del responsable, confidencialidad, medidas de seguridad equivalentes, no subcontratar sin autorización y eliminar/devolver los datos al término. (Plantilla: `Documents/CONTRATO-ENCARGO-TRATAMIENTO-DPA.md`.)

## 10. Herramientas de desarrollo con IA

La plataforma fue desarrollada con asistencia de herramientas de IA bajo supervisión humana. **Ninguna función de IA opera sobre datos personales en tiempo de ejecución**, y ningún dato personal de usuarios o terceros fue utilizado para entrenar modelos. Las obligaciones de divulgación aplicables a sistemas de IA no resultan aplicables a esta plataforma.

## 11. Cambios

Cualquier modificación sustancial de esta política se comunicará a los usuarios dentro de la plataforma y con fecha de versión actualizada.
