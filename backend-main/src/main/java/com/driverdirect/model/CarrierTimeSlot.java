package com.driverdirect.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A working time-of-day window on a date. Time slots are a road-driver
 * convenience that roll up (by mode) into the carrier's per-mode availability;
 * they default to ROAD.
 */
@Entity
@Table(name = "carrier_time_slot")
@Data
@NoArgsConstructor
public class CarrierTimeSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "carrier_id", nullable = false)
    private Carrier carrier;

    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Shipment.Mode mode = Shipment.Mode.ROAD;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    public CarrierTimeSlot(Carrier carrier, LocalDate date, Shipment.Mode mode,
                           LocalTime startTime, LocalTime endTime) {
        this.carrier = carrier;
        this.date = date;
        this.mode = mode != null ? mode : Shipment.Mode.ROAD;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    /** Back-compat: a slot with no explicit mode is ROAD. */
    public CarrierTimeSlot(Carrier carrier, LocalDate date, LocalTime startTime, LocalTime endTime) {
        this(carrier, date, Shipment.Mode.ROAD, startTime, endTime);
    }
}
