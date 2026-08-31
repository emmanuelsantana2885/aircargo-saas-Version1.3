package com.aircargo.authservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

/**
 * Política de MFA (singleton id=1). Define el patrón de re-enrolamiento:
 * <ul>
 *   <li><b>last_reset_at</b> — epoch global. Todo MFA configurado antes de este
 *       instante se considera caduco (reinicio/actualización de la app).</li>
 *   <li><b>reset_on_startup</b> — si true (default), cada arranque del
 *       auth-service adelanta el epoch (fuerza re-enrolar a todos).</li>
 *   <li><b>max_age_days</b> — antigüedad máxima de un enrolamiento MFA (default
 *       7): si no hay reinicios, los enrolled anteriores a now()-max_age_days también caducan.</li>
 * </ul>
 */
@Entity
@Table(name = "mfa_policy")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MfaPolicy {

    @Id
    @Column(name = "id")
    private Integer id = 1;

    @Column(name = "last_reset_at", nullable = false)
    private OffsetDateTime lastResetAt;

    @Builder.Default
    @Column(name = "reset_on_startup", nullable = false)
    private Boolean resetOnStartup = true;

    @Builder.Default
    @Column(name = "max_age_days", nullable = false)
    private Integer maxAgeDays = 7;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}