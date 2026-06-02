package com.driverdirect.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
    @JoinColumn(name = "shipper_id", nullable = false)
    private Shipper shipper;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Mode mode = Mode.ROAD;

    // Intermodal (M2): when this Shipment is one leg of a multi-leg movement it
    // points at its Itinerary and carries its 1-indexed position. Null for a
    // standalone single-leg load, so pre-M2 rows are unaffected.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "itinerary_id")
    @ToString.Exclude
    private Itinerary itinerary;

    @Column(name = "leg_sequence")
    private Integer legSequence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShipmentStatus status = ShipmentStatus.PLANNED;

    @Column(length = 3, nullable = false)
    private String currency = "EUR";

    // Per-mode pricing inputs (M3b). All nullable; when the unit for this leg's
    // mode has a value here, PricingService prices on that basis, otherwise it
    // falls back to the carrier's rate × hours.
    @Column(name = "distance_km")
    private BigDecimal distanceKm;

    @Column(name = "weight_kg")
    private BigDecimal weightKg;

    @Column(name = "volume_m3")
    private BigDecimal volumeM3;

    @Column(name = "container_count")
    private Integer containerCount;

    @Column(name = "piece_count")
    private Integer pieceCount;

    // The basis actually used to price this leg + the resolved quantity, so the
    // charge is explainable (e.g. PER_CONTAINER × 2).
    @Enumerated(EnumType.STRING)
    @Column(name = "charge_unit")
    private ChargeUnit chargeUnit;

    @Column(name = "chargeable_quantity")
    private BigDecimal chargeableQuantity;

    // Carrier-payable cost for this leg (what the carrier earns). Populated by
    // PricingService; was previously never set.
    @Column(name = "total_rate")
    private BigDecimal totalRate;

    // Per-mode platform commission: the snapshotted rate (%), the resulting fee
    // amount, and the shipper-payable total = totalRate + commissionAmount.
    @Column(name = "commission_percent")
    private BigDecimal commissionPercent;

    @Column(name = "commission_amount")
    private BigDecimal commissionAmount;

    @Column(name = "shipper_total")
    private BigDecimal shipperTotal;

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

    // Read-only collections so LoadResponse can navigate the tree without an
    // extra repo round-trip. @ToString.Exclude prevents Lombok from looping.
    @OneToMany(mappedBy = "shipment", fetch = FetchType.LAZY)
    @OrderBy("sequence ASC")
    @ToString.Exclude
    private List<Stop> stops = new ArrayList<>();

    @OneToMany(mappedBy = "shipment", fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<ShipmentLine> shipmentLines = new ArrayList<>();

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