package com.aircargo.exportservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "dashboard_report")
@Getter
@Setter
@NoArgsConstructor
public class DashboardReportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "field_sources", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String fieldSources;

    @Column(name = "formulas", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String formulas;

    @Column(name = "scenario", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String scenario;

    @Column(name = "grouping", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String grouping;

    @Column(name = "chart_config", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String chartConfig;

    @Column(name = "is_shared", nullable = false)
    private boolean shared;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
