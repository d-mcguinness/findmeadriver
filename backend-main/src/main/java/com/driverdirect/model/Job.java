package com.driverdirect.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "jobs")
@Data
@NoArgsConstructor
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employer_id", nullable = false)
    private Employer employer;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(name = "pickup_location")
    private String pickupLocation;

    @Column(name = "delivery_location")
    private String deliveryLocation;

    @Column(name = "estimated_duration_hours", nullable = false)
    private Double estimatedDurationHours;

    @Column(name = "date_needed", nullable = false)
    private LocalDate dateNeeded;

    @Column(name = "rate_per_hour", nullable = false)
    private BigDecimal ratePerHour;

    // ISO-4217 currency for ratePerHour (e.g. EUR, GBP).
    @Column(length = 3, nullable = false)
    private String currency = "EUR";

    // ISO-3166-1 alpha-2 country codes for pickup and delivery. Same value
    // for domestic jobs; different for cross-border (e.g. IE → GB).
    @Column(name = "pickup_country", length = 2, nullable = false)
    private String pickupCountry = "IE";

    @Column(name = "delivery_country", length = 2, nullable = false)
    private String deliveryCountry = "IE";

    // Free-form licence category required by the job. Matches the Driver
    // licenceCategory via plain equality.
    @Column(name = "required_licence_category")
    private String requiredLicenceCategory;

    /**
     * @deprecated retained for seed compatibility — read/write
     * {@link #requiredLicenceCategory} instead.
     */
    @Deprecated
    public Driver.CDLType getRequiredCdlType() {
        return requiredLicenceCategory == null ? null : Driver.CDLType.valueOf(requiredLicenceCategory);
    }

    /** @deprecated use {@link #setRequiredLicenceCategory(String)}. */
    @Deprecated
    public void setRequiredCdlType(Driver.CDLType t) {
        this.requiredLicenceCategory = t == null ? null : t.name();
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_driver_id")
    private Driver assignedDriver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status = JobStatus.OPEN;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
