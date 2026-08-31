package com.aircargo.authservice.service;

import com.aircargo.authservice.entity.AppUser;
import com.aircargo.authservice.entity.MfaPolicy;
import com.aircargo.authservice.repository.MfaPolicyRepository;
import com.aircargo.authservice.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Política de MFA: decide si un usuario debe (re)configurar su MFA antes de
 * operar. Combina dos reglas complementarias:
 *
 * <ol>
 *   <li><b>Reinicio/actualización de la app</b> ({@code app.mfa.reset-on-startup}): cada arranque
 *       del auth-service adelanta {@code last_reset_at} (lo hace
 *       {@link com.aircargo.authservice.config.MfaStartupResetRunner}). Cualquier MFA enrolado
 *       ANTES de ese instante caduca → el siguiente login exige re-enrolar.</li>
 *   <li><b>Antigüedad máxima</b> ({@code app.mfa.max-age-days}, default 7): si no hay reinicios,
 *       los enrolamientos con más de N días caducan igualmente.</li>
 * </ol>
 *
 * Cuando la política exige re-enrolar se emite el flujo 428 (misma puerta que el
 * enrolamiento inicial), con {@code mfaReason} para que el frontend muestre la
 * advertencia de seguridad adecuada (required | reset | expired).
 */
@Service
public class MfaPolicyService {

    private static final Logger log = LoggerFactory.getLogger(MfaPolicyService.class);

    /** Estado del enrolamiento MFA evaluado contra la política. */
    public enum MfaEligibility {
        /** MFA válido y al día — no requiere acción. */
        OK,
        /** Nunca se configuró MFA (o sin timestamp de enrolamiento). */
        REQUIRED,
        /** MFA configurado pero ANTES del último reinicio/actualización → re-enrolar. */
        RESET_REQUIRED,
        /** MFA configurado hace más de max_age_days → re-enrolar. */
        EXPIRED
    }

    private final MfaPolicyRepository policyRepository;
    private final AppUserRepository userRepository;
    private final int defaultMaxAgeDays;
    private final boolean defaultResetOnStartup;

    public MfaPolicyService(MfaPolicyRepository policyRepository,
                            AppUserRepository userRepository,
                            @Value("${app.mfa.max-age-days:7}") int defaultMaxAgeDays,
                            @Value("${app.mfa.reset-on-startup:true}") boolean defaultResetOnStartup) {
        this.policyRepository = policyRepository;
        this.userRepository = userRepository;
        this.defaultMaxAgeDays = defaultMaxAgeDays;
        this.defaultResetOnStartup = defaultResetOnStartup;
    }

    /** Fila singleton (id=1), creada bajo demanda con defaults si no existe (tests/BD sin migración). */
    @Transactional
    public MfaPolicy policy() {
        return policyRepository.findById(1).orElseGet(() -> {
            MfaPolicy p = MfaPolicy.builder()
                    .id(1)
                    // below sentinel distante: no implica "reinicio reciente"; solo la regla
                    // de antigüedad (max_age_days) puede invalidar enrolamientos existentes.
                    .lastResetAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(3650))
                    .resetOnStartup(defaultResetOnStartup)
                    .maxAgeDays(defaultMaxAgeDays)
                    .build();
            policyRepository.save(p);
            log.info("MfaPolicy singleton creado con defaults (maxAgeDays={}, resetOnStartup={})",
                    defaultMaxAgeDays, defaultResetOnStartup);
            return p;
        });
    }

    /**
     * Evalúa si el usuario está OK o debe (re)configurar MFA y por qué.
     * <ul>
     *   <li><b>REQUIRED</b> — nunca enrolado (o enrolado legado sin timestamp).</li>
     *   <li><b>RESET_REQUIRED</b> — enrolado antes del último reinicio de la app.</li>
     *   <li><b>EXPIRED</b> — enrolado hace más de max_age_days (sin reinicios recientes).</li>
     *   <li><b>OK</b> — enviado.</li>
     * </ul>
     */
    public MfaEligibility evaluate(AppUser user) {
        if (user == null) return MfaEligibility.OK;
        if (!Boolean.TRUE.equals(user.getMfaEnabled()) || user.getMfaEnrolledAt() == null) {
            return MfaEligibility.REQUIRED;
        }
        MfaPolicy p = policy();
        OffsetDateTime enrolledAt = user.getMfaEnrolledAt();
        if (p.getLastResetAt() != null && enrolledAt.isBefore(p.getLastResetAt())) {
            return MfaEligibility.RESET_REQUIRED;
        }
        int maxAgeDays = p.getMaxAgeDays() != null ? p.getMaxAgeDays() : 1;
        if (enrolledAt.isBefore(OffsetDateTime.now(ZoneOffset.UTC).minusDays(maxAgeDays))) {
            return MfaEligibility.EXPIRED;
        }
        return MfaEligibility.OK;
    }

    /** true si el usuario debe (re)configurar MFA por cualquier regla. */
    public boolean requiresReenrollment(AppUser user) {
        return evaluate(user) != MfaEligibility.OK;
    }

    /** Adelanta el epoch global (reinicio/actualización) → todo MFA previo caduca. */
    @Transactional
    public void resetNow() {
        MfaPolicy p = policy();
        p.setLastResetAt(OffsetDateTime.now(ZoneOffset.UTC));
        policyRepository.save(p);
        log.info("MFA policy epoch adelantado a {} — todos los MFA configurados antes de este instante caducan",
                p.getLastResetAt());
    }

    /** ¿El siguiente arranque debe forzar re-enrolamiento global? (default true). */
    public boolean isResetOnStartupEnabled() {
        return Boolean.TRUE.equals(policy().getResetOnStartup());
    }
}