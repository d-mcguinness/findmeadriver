package com.driverdirect.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * An ordered physical event on a Shipment. Sequence is 1-indexed within the
 * parent Shipment. earliestAt/latestAt define the appointment window;
 * actualAt is set when the carrier checks in/out.
 *
 * <p>{@link StopType} covers commercial stops (PICKUP / DELIVERY / WAYPOINT)
 * plus international-route bookkeeping stops (REST, BORDER, FERRY_TERMINAL,
 * EUROTUNNEL) that affect tachograph timing and cost without representing a
 * goods event. Bookkeeping stops still attach to a {@link Location} (the
 * service area, border post, or terminal).
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

    public enum StopType {
        // Commercial events: goods change hands.
        PICKUP, DELIVERY, WAYPOINT,
        // Bookkeeping events: affect tacho clock and cost, no goods movement.
        REST, BORDER, FERRY_TERMINAL, EUROTUNNEL
    }
}
