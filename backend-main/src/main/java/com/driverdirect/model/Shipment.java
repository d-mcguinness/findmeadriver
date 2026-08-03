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

    // How distanceKm was arrived at. Two paths write this column with numbers
    // of different kinds — a client's measured driving distance and the routing
    // engine's coordinate model, which for road differ by the circuity factor
    // (~30%) — and PER_KM pricing turns whichever one lands here into real
    // money. Recording the basis keeps a model from silently reading as a
    // measurement. Null on legacy/seed rows and on legs with no distance.
    @Enumerated(EnumType.STRING)
    @Column(name = "distance_source")
    private DistanceSource distanceSource;

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

    /**
     * International-vs-domestic classification of this leg, derived from its
     * origin/destination countries. UNKNOWN when either is missing (strict —
     * not silently treated as domestic). See {@link MovementType}.
     */
    public MovementType getMovementType() {
        return MovementType.of(originCountry, destinationCountry);
    }

    public enum Mode { ROAD, RAIL, OCEAN, AIR, INTERMODAL, PARCEL }

    public enum ShipmentStatus {
        PLANNED, TENDERED, ACCEPTED, DISPATCHED, IN_TRANSIT, DELIVERED, CANCELLED
    }

    /**
     * Where {@link #distanceKm} came from — what the server can actually
     * vouch for, not what it wishes were true.
     *
     * <p>{@code CLIENT_SUPPLIED}: the distance arrived on the request. The
     * post-a-load form fills it from the Google Routes API, i.e. measured
     * driving distance along a real path, but the shipper may edit it, so the
     * server can only say a client stated it.
     *
     * <p>{@code GREAT_CIRCLE_ESTIMATE}: the routing engine derived it from the
     * endpoints' coordinates — haversine for a scheduled rail/sea/air leg, and
     * haversine × {@code RoutePlanner.ROAD_CIRCUITY} for a virtual road leg.
     * A model, not a measurement: no server-side routing API exists (and none
     * exists at all for maritime), so an accepted route's road legs read short
     * of true driving distance and its sea legs ignore canals and straits.
     */
    public enum DistanceSource { CLIENT_SUPPLIED, GREAT_CIRCLE_ESTIMATE }
}