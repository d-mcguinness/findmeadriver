package com.driverdirect.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Phase 0 TMS data model: the Load entity is now the carrier-assignment leg
 * (the "Load" in TMS-speak). All customer-facing metadata — title,
 * description, pickup/delivery addresses, dateNeeded, origin/destination
 * country — lives on the linked Shipment + TransportOrder + Stops. The
 * legacy getters below navigate the tree so existing callers (services,
 * DTOs) keep compiling untouched.
 */
@Entity
@Table(name = "loads")
@Data
@NoArgsConstructor
public class Load {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipper_id", nullable = false)
    private Shipper shipper;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_id")
    private Shipment shipment;

    @Column(name = "estimated_duration_hours", nullable = false)
    private Double estimatedDurationHours;

    @Column(name = "rate_per_hour", nullable = false)
    private BigDecimal ratePerHour;

    // ISO-4217 currency for ratePerHour (e.g. EUR, GBP). Stays on the Load
    // since pricing is per-assignment; customer-facing currency lives on the
    // TransportOrder.
    @Column(length = 3, nullable = false)
    private String currency = "EUR";

    // Free-form licence category required by the load. Matches the Carrier
    // licenceCategory via plain equality.
    @Column(name = "required_licence_category")
    private String requiredLicenceCategory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_carrier_id")
    private Carrier assignedCarrier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoadStatus status = LoadStatus.OPEN;

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

    /**
     * @deprecated retained for seed compatibility — read/write
     * {@link #requiredLicenceCategory} instead.
     */
    @Deprecated
    public Carrier.CDLType getRequiredCdlType() {
        // requiredLicenceCategory may hold non-US values ("C", "CE",
        // "HGV_CLASS_1") that aren't CDLType constants — return null rather
        // than throwing, mirroring Carrier.getCdlType().
        if (requiredLicenceCategory == null) return null;
        try {
            return Carrier.CDLType.valueOf(requiredLicenceCategory);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** @deprecated use {@link #setRequiredLicenceCategory(String)}. */
    @Deprecated
    public void setRequiredCdlType(Carrier.CDLType t) {
        this.requiredLicenceCategory = t == null ? null : t.name();
    }

    // ---- Tree-navigation getter shims (replaces dropped columns) ----

    public String getTitle() {
        TransportOrder o = orderOf();
        return o != null ? o.getTitle() : null;
    }

    public String getDescription() {
        TransportOrder o = orderOf();
        return o != null ? o.getDescription() : null;
    }

    public LocalDate getDateNeeded() {
        TransportOrder o = orderOf();
        return o != null ? o.getDateNeeded() : null;
    }

    public String getPickupLocation() {
        Stop s = stopOfType(Stop.StopType.PICKUP);
        return s != null && s.getLocation() != null ? s.getLocation().getName() : null;
    }

    public String getDeliveryLocation() {
        Stop s = stopOfType(Stop.StopType.DELIVERY);
        return s != null && s.getLocation() != null ? s.getLocation().getName() : null;
    }

    public String getPickupCountry() {
        return shipment != null ? shipment.getOriginCountry() : null;
    }

    public String getDeliveryCountry() {
        return shipment != null ? shipment.getDestinationCountry() : null;
    }

    /**
     * Transport mode of this Load, read off the linked Shipment leg. Defaults
     * to ROAD for a Load not yet linked to a Shipment, mirroring the tree-
     * navigation shims above. This is the dispatch key for mode-aware pricing
     * and compliance.
     */
    public Shipment.Mode getMode() {
        return shipment != null && shipment.getMode() != null
                ? shipment.getMode() : Shipment.Mode.ROAD;
    }

    private TransportOrder orderOf() {
        if (shipment == null || shipment.getShipmentLines() == null
                || shipment.getShipmentLines().isEmpty()) return null;
        return shipment.getShipmentLines().get(0).getOrder();
    }

    private Stop stopOfType(Stop.StopType type) {
        if (shipment == null || shipment.getStops() == null) return null;
        return shipment.getStops().stream()
                .filter(s -> s.getType() == type)
                .findFirst()
                .orElse(null);
    }
}
