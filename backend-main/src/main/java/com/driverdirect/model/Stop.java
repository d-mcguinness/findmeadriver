package com.driverdirect.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * An ordered physical event on a Shipment — pickup, delivery, or a
 * pass-through waypoint. Sequence is 1-indexed within the parent Shipment.
 * earliestAt/latestAt define the appointment window; actualAt is set when
 * the driver checks in/out.
 */
@Entity
@Table(name = "stops",
        uniqueConstraints = @UniqueConstraint(columnNames = {"shipment_id", "sequence"}))
@Data
@NoArgsConstructor
public class Stop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false)
    private Shipment shipment;

    @Column(nullable = false)
    private int sequence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StopType type;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(name = "earliest_at")
    private LocalDateTime earliestAt;

    @Column(name = "latest_at")
    private LocalDateTime latestAt;

    @Column(name = "actual_at")
    private LocalDateTime actualAt;

    public enum StopType { PICKUP, DELIVERY, WAYPOINT }
}
