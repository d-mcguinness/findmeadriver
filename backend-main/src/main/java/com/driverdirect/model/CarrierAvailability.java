package com.driverdirect.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * A carrier's declared availability for one transport mode on one date — one
 * row per (carrier, date, mode). Per-mode duty clocks: a multi-modal carrier
 * keeps a separate availability calendar per mode, each governed by that mode's
 * own duty/rest ceilings.
 */
@Entity
@Table(name = "carrier_availability",
       uniqueConstraints = @UniqueConstraint(columnNames = {"carrier_id", "date", "mode"}))
@Data
@NoArgsConstructor
public class CarrierAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "carrier_id", nullable = false)
    private Carrier carrier;

    @Column(nullable = false)
    private LocalDate date;

    /** The transport mode this availability is declared for. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Shipment.Mode mode = Shipment.Mode.ROAD;

    @Column(name = "available_hours", nullable = false)
    private Double availableHours;

    public CarrierAvailability(Carrier carrier, LocalDate date, Shipment.Mode mode, Double availableHours) {
        this.carrier = carrier;
        this.date = date;
        this.mode = mode != null ? mode : Shipment.Mode.ROAD;
        this.availableHours = availableHours;
    }

    /** Back-compat: an availability with no explicit mode is ROAD. */
    public CarrierAvailability(Carrier carrier, LocalDate date, Double availableHours) {
        this(carrier, date, Shipment.Mode.ROAD, availableHours);
    }
}
