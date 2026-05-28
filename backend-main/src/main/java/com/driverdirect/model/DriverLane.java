package com.driverdirect.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * A directional country pair the driver is willing to take work on
 * (e.g. {@code IE → FR}). Cross-border drivers usually only see a small set
 * of routes that make sense for their base; when at least one lane is
 * configured the browse-jobs query filters to that lane set.
 *
 * <p>A driver with zero lanes sees all OPEN jobs (existing behaviour
 * preserved). Lanes are directional — set both {@code IE → FR} and
 * {@code FR → IE} if you want a round-trip pair.
 */
@Entity
@Table(name = "driver_lanes",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"driver_id", "origin_country", "destination_country"}),
        indexes = @Index(name = "idx_driver_lanes_driver", columnList = "driver_id"))
@Data
@NoArgsConstructor
public class DriverLane {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @NotBlank
    @Size(min = 2, max = 2)
    @Column(name = "origin_country", length = 2, nullable = false)
    private String originCountry;

    @NotBlank
    @Size(min = 2, max = 2)
    @Column(name = "destination_country", length = 2, nullable = false)
    private String destinationCountry;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (originCountry != null) originCountry = originCountry.toUpperCase();
        if (destinationCountry != null) destinationCountry = destinationCountry.toUpperCase();
    }
}
