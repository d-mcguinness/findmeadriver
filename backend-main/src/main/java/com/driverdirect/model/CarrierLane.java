package com.driverdirect.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * A directional country pair the carrier is willing to take work on
 * (e.g. {@code IE → FR}). Cross-border carriers usually only see a small set
 * of routes that make sense for their base; when at least one lane is
 * configured the browse-loads query filters to that lane set.
 *
 * <p>A carrier with zero lanes sees all OPEN loads (existing behaviour
 * preserved). Lanes are directional — set both {@code IE → FR} and
 * {@code FR → IE} if you want a round-trip pair.
 */
@Entity
@Table(name = "carrier_lanes",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"carrier_id", "origin_country", "destination_country"}),
        indexes = @Index(name = "idx_carrier_lanes_carrier", columnList = "carrier_id"))
@Data
@NoArgsConstructor
public class CarrierLane {

    /** Sanity ceiling for one scheduled leg (a year). Shared by the API
     *  validation and the graph build's fail-soft guard: an absurd or
     *  non-finite stored value would otherwise overflow Duration arithmetic
     *  when the lane is compiled into a ScheduledServiceEdge. */
    public static final double MAX_TRANSIT_HOURS = 24 * 365;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "carrier_id", nullable = false)
    private Carrier carrier;

    @NotBlank
    @Size(min = 2, max = 2)
    @Column(name = "origin_country", length = 2, nullable = false)
    private String originCountry;

    @NotBlank
    @Size(min = 2, max = 2)
    @Column(name = "destination_country", length = 2, nullable = false)
    private String destinationCountry;

    // ---- Timetable (routing-engine build-order step 2; see README.md) ----
    // All nullable and additive: a lane without them stays a pure country-pair
    // browse preference, exactly as before. A *timetabled* lane is a scheduled
    // service the future routing graph turns into a ScheduledServiceEdge:
    // a recurring weekly pattern (days + local departure time + transit hours),
    // optionally anchored terminal-to-terminal via typed Locations.

    /** Mode of the scheduled service (RAIL/OCEAN/AIR; road lanes are normally
     *  untimetabled since road legs are generated virtually). Null when the
     *  lane is just a country-pair preference. */
    @Enumerated(EnumType.STRING)
    @Column(name = "service_mode")
    private Shipment.Mode serviceMode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_location_id")
    private Location originLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_location_id")
    private Location destinationLocation;

    /** Comma-separated {@link DayOfWeek} names (e.g. "MONDAY,THURSDAY").
     *  Stored flat rather than as an @ElementCollection so the browse query
     *  and graph build read lanes without an extra join. */
    @Column(name = "departure_days", length = 80)
    private String departureDays;

    @Column(name = "departure_time")
    private LocalTime departureTime;

    @Column(name = "transit_duration_hours")
    private Double transitDurationHours;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (originCountry != null) originCountry = originCountry.toUpperCase();
        if (destinationCountry != null) destinationCountry = destinationCountry.toUpperCase();
    }

    /** True when this lane carries a usable schedule (all three parts set). */
    public boolean isTimetabled() {
        return departureTime != null && transitDurationHours != null
                && departureDays != null && !departureDays.isBlank();
    }

    /** Parsed view of {@link #departureDays}; empty when untimetabled. An
     *  unrecognised stored token is skipped (fail soft), not thrown on. */
    public Set<DayOfWeek> getDepartureDaySet() {
        if (departureDays == null || departureDays.isBlank()) return Set.of();
        Set<DayOfWeek> out = EnumSet.noneOf(DayOfWeek.class);
        for (String token : departureDays.split(",")) {
            try {
                out.add(DayOfWeek.valueOf(token.trim().toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                // skip unrecognised token
            }
        }
        return out;
    }

    /**
     * Earliest scheduled departure at or after {@code after} — the contract
     * the routing graph's ScheduledServiceEdge.nextDeparture() will delegate
     * to. Null when the lane is untimetabled. Times are local to the origin;
     * timezone resolution (via Location.timezone) is the graph build's job.
     */
    public LocalDateTime nextDeparture(LocalDateTime after) {
        if (!isTimetabled()) return null;
        Set<DayOfWeek> days = getDepartureDaySet();
        if (days.isEmpty()) return null;
        for (int i = 0; i <= 7; i++) {
            LocalDateTime candidate = LocalDateTime.of(after.toLocalDate().plusDays(i), departureTime);
            if (days.contains(candidate.getDayOfWeek()) && !candidate.isBefore(after)) {
                return candidate;
            }
        }
        return null; // unreachable with a non-empty day set, but be safe
    }
}
