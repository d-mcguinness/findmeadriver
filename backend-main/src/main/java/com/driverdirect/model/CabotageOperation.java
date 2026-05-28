package com.driverdirect.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A historical record of a completed cabotage operation — a domestic
 * carriage performed by a foreign-based driver inside a host country.
 *
 * <p>Cabotage rules (EU Regulation 1072/2009, Mobility Package 2020):
 * after an international delivery into Member State X, a non-resident
 * haulier may perform up to 3 cabotage ops in X within 7 days of
 * unloading the international cargo. This entity captures the historical
 * fact of the op; the {@link com.driverdirect.service.CabotageService}
 * applies the count + 7-day window check at apply time.
 *
 * <p>Simplification vs. the full regulation: we count cabotage ops in the
 * destination country in the last 7 days regardless of which international
 * delivery anchored the window. This catches the common over-limit case
 * defensibly; tightening to per-anchor windows is a documented follow-up.
 */
@Entity
@Table(name = "cabotage_operations",
        indexes = {
                @Index(name = "idx_cabotage_driver_country_date",
                        columnList = "driver_id, country, performed_at"),
                @Index(name = "idx_cabotage_job", columnList = "job_id")
        })
@Data
@NoArgsConstructor
public class CabotageOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    /** Source Job. Nullable so back-fill / imported history can omit it. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id")
    private Job job;

    /**
     * The delivery (unload) Location this op was carried to. Provenance only —
     * cabotage is counted by {@link #country}, never by this field. Captured at
     * record time from the final DELIVERY stop; nullable for back-filled or
     * imported history. Read it for audit/dispute ("where did these ops happen?")
     * — do NOT derive {@link #country} from {@code deliveryLocation.country} at
     * read time: that would let a later Location edit rewrite a historical fact.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_location_id")
    private Location deliveryLocation;

    /** Host country (ISO-3166 alpha-2). Always uppercase. */
    @Column(length = 2, nullable = false)
    private String country;

    @Column(name = "performed_at", nullable = false)
    private LocalDate performedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (country != null) country = country.toUpperCase();
    }
}
