package com.driverdirect.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The physical move. One or more TransportOrders attach via ShipmentLine;
 * one or more Stops live underneath. Carrier-side execution status lives
 * here (TENDERED → ACCEPTED → IN_TRANSIT → DELIVERED).
 */
@Entity
@Table(name = "shipments")
@Data
@NoArgsConstructor
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employer_id", nullable = false)
    private Employer employer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Mode mode = Mode.ROAD;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShipmentStatus status = ShipmentStatus.PLANNED;

    @Column(length = 3, nullable = false)
    private String currency = "EUR";

    @Column(name = "total_rate")
    private BigDecimal totalRate;

    @Column(name = "tendered_at")
    private LocalDateTime tenderedAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    // Derived/cached for fast lane filtering and analytics.
    @Column(name = "origin_country", length = 2)
    private String originCountry;

    @Column(name = "destination_country", length = 2)
    private String destinationCountry;

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

    public enum Mode { ROAD, RAIL, OCEAN, AIR, INTERMODAL, PARCEL }

    public enum ShipmentStatus {
        PLANNED, TENDERED, ACCEPTED, DISPATCHED, IN_TRANSIT, DELIVERED, CANCELLED
    }
}