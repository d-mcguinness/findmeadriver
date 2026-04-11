package com.driverdirect.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "driver_availability",
       uniqueConstraints = @UniqueConstraint(columnNames = {"driver_id", "date"}))
@Data
@NoArgsConstructor
public class DriverAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "available_hours", nullable = false)
    private Double availableHours;

    public DriverAvailability(Driver driver, LocalDate date, Double availableHours) {
        this.driver = driver;
        this.date = date;
        this.availableHours = availableHours;
    }
}
