package com.aircargo.authservice.config;

import com.aircargo.authservice.event.AuditEventType;
import com.aircargo.authservice.service.AuditService;
import com.aircargo.authservice.service.MfaPolicyService;
import com.aircargo.authservice.service.TokenRevocationService;
import com.aircargo.authservice.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Reinicio de la política MFA en cada arranque del auth-service
 * ({@code app.mfa.reset-on-startup=true}, default). Efecto:
 *
 * <ol>
 *   <li>Adelanta el epoch global ({@code mfa_policy.last_reset_at}) → todo MFA
 *       configurado antes de este boot caduca.</li>
 *   <li>Revoca las sesiones de los usuarios con MFA habilitado (tokens_valid_from
 *       adelantado) → deben volver a loguearse, y el login les exige re-enrolar MFA
 *       (428 con {@code mfaReason=reset}).</li>
 *   <li>Audita {@code MFA_POLICY_RESET}.</li>
 * </ol>
 *
 * Deshabilitable con {@code app.mfa.reset-on-startup=false} (p.ej. en tests o en
 * despliegues donde el epoch se mueve por otro medio). Excluido del perfil test.
 */
@Component
@Profile("!test")
public class MfaStartupResetRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MfaStartupResetRunner.class);

    private final MfaPolicyService mfaPolicyService;
    private final AppUserRepository userRepository;
    private final AuditService auditService;
    private final TokenRevocationService tokenRevocationService;

    public MfaStartupResetRunner(MfaPolicyService mfaPolicyService,
                                 AppUserRepository userRepository,
                                 AuditService auditService,
                                 TokenRevocationService tokenRevocationService) {
        this.mfaPolicyService = mfaPolicyService;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.tokenRevocationService = tokenRevocationService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        try {
            if (!mfaPolicyService.isResetOnStartupEnabled()) {
                log.info("MFA policy: reset-on-startup deshabilitado — se respetan los enrolamientos vigentes");
                return;
            }
            mfaPolicyService.resetNow();
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            int revoked = userRepository.revokeSessionsForMfaUsers(now);
            auditService.log(null, "system", "System",
                    AuditEventType.MFA_POLICY_RESET, "POLICY", "mfa_policy",
                    "{\"revokedSessions\":" + revoked + ", \"reason\":\"startup\"}", null);
            log.warn("MFA policy reiniciada en arranque: {} sesiones de usuarios MFA revocadas — todos deben re-enrolar",
                    revoked);
        } catch (Exception e) {
            // El arranque NO debe abortar por un fallo de la política MFA: se loguea y se
            // deja la política sin mover (los logins siguen validando contra el epoch actual).
            log.error("MFA policy reset en arranque falló (se omite): {}", e.getMessage());
        }
    }
}