package com.aircargo.bookingservice.entity;

import com.aircargo.common.entity.Airline;
import com.aircargo.bookingservice.entity.Flight;
import com.aircargo.common.entity.CommodityType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "booking")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "airline_id", nullable = false)
    private Airline airline;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_id")
    private Flight flight;

    @Column(name = "mawb_id")
    private UUID mawbId;

    @Column(name = "client_name", length = 200)
    private String clientName;

    @Column(name = "contact_name", length = 150)
    private String contactName;

    @Column(name = "cnee", length = 200)
    private String cnee;

    @Column(name = "shipper_name", length = 200)
    private String shipperName;

    @Column(name = "awb_number", length = 50)
    private String awbNumber;

    @Column(name = "skids")
    private Integer skids;

    @Column(name = "units")
    private Integer units;

    @Enumerated(EnumType.STRING)
    @Column(name = "ea_type", length = 10)
    private EaType eaType;

    @Column(name = "reserved_kg", precision = 10, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal reservedKg = BigDecimal.ZERO;

    @Column(name = "confirmed_kg", precision = 10, scale = 2)
    private BigDecimal confirmedKg;

    @Column(name = "received_kg", precision = 10, scale = 2)
    private BigDecimal receivedKg;

    @Column(name = "fulfillment_pct", precision = 10, scale = 4)
    private BigDecimal fulfillmentPct;

    @Column(name = "destination", length = 100)
    private String destination;

    @Column(name = "priority", length = 50)
    private String priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "commodity_type", length = 30)
    private CommodityType commodityType;

    @Column(name = "day_received")
    private LocalDate dayReceived;

    @Column(name = "time_hours", length = 10)
    private String timeHours;

    @Column(name = "positions")
    private Integer positions;

    @Column(name = "real_positions")
    private Integer realPositions;

    @Column(name = "last_week_kg", precision = 10, scale = 2)
    private BigDecimal lastWeekKg;

    @Column(name = "last_week_positions")
    private Integer lastWeekPositions;

    @Column(name = "is_confirmed")
    @Builder.Default
    private Boolean isConfirmed = false;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}