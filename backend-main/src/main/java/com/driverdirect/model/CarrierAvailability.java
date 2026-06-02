package com.driverdirect.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "carrier_availability",
       uniqueConstraints = @UniqueConstraint(columnNames = {"carrier_id", "date"}))
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

    @Column(name = "available_hours", nullable = false)
    private Double availableHours;

    public CarrierAvailability(Carrier carrier, LocalDate date, Double availableHours) {
        this.carrier = carrier;
        this.date = date;
        this.availableHours = availableHours;
    }
}
