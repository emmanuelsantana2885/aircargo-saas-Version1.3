<template>
  <div class="min-h-screen bg-white text-slate-900 font-sans">
    <div class="max-w-3xl mx-auto px-5 py-10">
      <div class="flex items-center justify-between mb-8">
        <h1 class="text-2xl font-bold">{{ t('privacy.title') }}</h1>
        <router-link to="/login" class="text-[13px] text-slate-500 hover:text-slate-900 hover:underline">&larr; {{ t('common.back') }}</router-link>
      </div>
      <p class="text-[12px] text-slate-400 mb-6">{{ t('privacy.version') }} · v1.0 · 2026</p>

      <div class="space-y-6 text-[14px] leading-relaxed text-slate-700">
        <section v-for="s in sections" :key="s.n">
          <h2 class="text-[15px] font-bold text-slate-900 mb-2">{{ s.n }}. {{ s.title }}</h2>
          <div v-for="(p, i) in s.paragraphs" :key="i" class="mb-1.5" v-html="p"></div>
          <table v-if="s.table" class="w-full my-2 border border-slate-200 rounded overflow-hidden text-[13px]">
            <tbody>
              <tr v-for="row in s.table" :key="row[0]" class="border-b border-slate-100">
                <td class="px-3 py-1.5 font-semibold w-1/3 bg-slate-50 align-top">{{ row[0] }}</td>
                <td class="px-3 py-1.5" v-html="row[1]"></td>
              </tr>
            </tbody>
          </table>
          <ul v-if="s.list" class="list-disc pl-6 space-y-1">
            <li v-for="li in s.list" :key="li" v-html="li"></li>
          </ul>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const { locale, t } = useI18n()
const ES = locale.value === 'es' || !locale.value.startsWith('en')

const sections = computed(() => ES ? esSections : enSections)

const esSections = [
  { n: 1, title: 'Responsable del tratamiento', paragraphs: [
    'El responsable del tratamiento de los datos personales tratados en esta plataforma es <b>[EMPRESA_OPERADORA]</b>. Contacto: <b>[EMAIL_CONTACTO]</b>.' ] },
  { n: 2, title: 'Datos que tratamos', table: [
    ['Usuarios del sistema', 'Nombre, correo electrónico, rol, aerolínea, hash de contraseña, secreto de autenticación de dos factores (cifrado), dirección IP y fecha/hora de acceso.'],
    ['Registro de auditoría', 'Usuario, acción, entidad afectada, IP y fecha/hora de cada operación.'],
    ['Terceros en operaciones de carga', 'Nombre, cédula o documento de identidad, firma digitalizada e imagen del documento (recibos de bodega).'],
    ['Datos operativos', 'Reservas, guías aéreas, ULDs, vuelos, pesos y dimensiones.'] ] },
  { n: 3, title: 'Finalidades', paragraphs: [], list: [
    'Operación logística de carga aérea (reservas, recibos, manifiestos, trazabilidad).',
    'Seguridad de la información: autenticación, control de acceso por roles y auditoría de acciones.',
    'Cumplimiento de obligaciones legales y aduaneras del transporte de carga.',
    'No usamos cookies publicitarias ni analítica de terceros; no vendemos datos; no realizamos perfilamiento automatizado.' ] },
  { n: 4, title: 'Consentimiento y base legal', paragraphs: [
    'Los datos de terceros receptores de carga se tratan con su consentimiento expreso, otorgado mediante la firma del recibo de bodega (Art. 5, Ley 172-13). Los datos de usuarios internos se tratan en el marco de su relación laboral o contractual. Los registros de auditoría responden al interés legítimo de seguridad informática.' ] },
  { n: 5, title: 'Conservación', paragraphs: [
    'Recibos y datos operativos durante la relación comercial más [5] años. Registros de auditoría por [24] meses. Los tokens de restablecimiento de contraseña expiran en 15 minutos. Las sesiones inactivas se purgan automáticamente.' ] },
  { n: 6, title: 'Seguridad', paragraphs: [], list: [
    'Contraseñas guardadas solo como hash BCrypt.',
    'Cédulas, documentos de identidad, firmas y secretos de doble factor <b>cifrados AES-256</b> en reposo.',
    'Transporte cifrado (TLS) y control de acceso por roles.',
    'Bloqueo de cuentas tras 5 intentos fallidos; revocación central de sesiones al bloquear o cambiar contraseña.',
    'Copias de seguridad diarias con retención de 30 días.' ] },
  { n: 7, title: 'Sus derechos (ARCO)', paragraphs: [
    'Usted puede ejercer sus derechos de <b>acceso, actualización, rectificación, cancelación y oposición</b> escribiendo a <b>[EMAIL_CONTACTO]</b>. Responderemos en un plazo máximo de 10 días hábiles. Contra una respuesta insatisfactoria procede la acción de habeas data (Constitución, Art. 44).' ] },
  { n: 8, title: 'Transferencias', paragraphs: [
    'Los datos se almacenan en servidores bajo control del responsable y no se transfieren internacionalmente ni se ceden a terceros, salvo obligación legal.' ] },
]

const enSections = [
  { n: 1, title: 'Data controller', paragraphs: [
    'The data controller for personal data processed in this platform is <b>[COMPANY_NAME]</b>. Contact: <b>[CONTACT_EMAIL]</b>.' ] },
  { n: 2, title: 'Data we process', table: [
    ['System users', 'Name, email, role, airline, password hash, two-factor secret (encrypted), IP address and access timestamps.'],
    ['Audit trail', 'User, action, affected entity, IP and timestamp of every operation.'],
    ['Third parties in cargo operations', 'Name, ID number, digitized signature and ID document image (warehouse receipts).'],
    ['Operational data', 'Bookings, air waybills, ULDs, flights, weights and dimensions.'] ] },
  { n: 3, title: 'Purposes', paragraphs: [], list: [
    'Air cargo logistics operations (bookings, receipts, manifests, ULD traceability).',
    'Information security: authentication, role-based access control and action auditing.',
    'Compliance with legal and customs obligations of cargo transport.',
    'We do not use advertising cookies or third-party analytics; we do not sell data or perform automated profiling.' ] },
  { n: 4, title: 'Consent and legal basis', paragraphs: [
    'Cargo receiver data is processed with their express consent, granted by signing the warehouse receipt (Art. 5, Law 172-13). Internal user data is processed within their employment/contractual relationship. Audit records respond to the legitimate interest of information security.' ] },
  { n: 5, title: 'Retention', paragraphs: [
    'Receipts and operational data for the duration of the business relationship plus [5] years. Audit records for [24] months. Password reset tokens expire after 15 minutes. Idle sessions are purged automatically.' ] },
  { n: 6, title: 'Security', paragraphs: [], list: [
    'Passwords stored only as BCrypt hashes.',
    'ID numbers, identity documents, signatures and 2FA secrets <b>AES-256 encrypted</b> at rest.',
    'Encrypted transport (TLS) and role-based access control.',
    'Account lockout after 5 failed attempts; central session revocation upon block or password change.',
    'Daily backups with 30-day retention.' ] },
  { n: 7, title: 'Your rights (ARCO)', paragraphs: [
    'You may exercise your rights of <b>access, updating, rectification, cancellation and objection</b> by writing to <b>[CONTACT_EMAIL]</b>. We will respond within 10 business days at most. Against an unsatisfactory response, the <i>habeas data</i> action is available (Constitution, Art. 44).' ] },
  { n: 8, title: 'Transfers', paragraphs: [
    'Data is stored on servers under the controller\u2019s control and is not transferred internationally nor disclosed to third parties, except under legal obligation.' ] },
]
</script>
