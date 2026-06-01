package com.driverdirect.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

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

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    public CarrierTimeSlot(Carrier carrier, LocalDate date, LocalTime startTime, LocalTime endTime) {
        this.carrier = carrier;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
    }
}