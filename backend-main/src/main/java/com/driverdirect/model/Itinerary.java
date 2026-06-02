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
 * A true door-to-door movement made of an ordered sequence of legs (M2). Each
 * leg is a single-mode {@link Shipment}; an Itinerary groups N of them for one
 * {@link TransportOrder} and carries the rolled-up totals. The mode INTERMODAL
 * lives only here (derived) — a leg always resolves to one concrete mode.
 *
 * <p>Single-leg loads (the pre-M2 norm) simply have no Itinerary — a Shipment's
 * {@code itinerary} is nullable, so existing rows are unaffected.
 */
@Entity
@Table(name = "itineraries")
@Data
@NoArgsConstructor
public class Itinerary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipper_id", nullable = false)
    private Shipper shipper;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private TransportOrder order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItineraryStatus status = ItineraryStatus.PLANNED;

    @Column(length = 3, nullable = false)
    private String currency = "EUR";

    // Rolled up from the legs by PricingService.recalcItinerary:
    //   carrierCostTotal = Σ leg carrier cost, commissionTotal = Σ leg fee,
    //   grandTotal = Σ leg shipper total (what the customer pays end-to-end).
    @Column(name = "carrier_cost_total")
    private BigDecimal carrierCostTotal;

    @Column(name = "commission_total")
    private BigDecimal commissionTotal;

    @Column(name = "grand_total")
    private BigDecimal grandTotal;

    // Cached from first leg origin / last leg destination.
    @Column(name = "origin_country", length = 2)
    private String originCountry;

    @Column(name = "destination_country", length = 2)
    private String destinationCountry;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "itinerary", fetch = FetchType.LAZY)
    @OrderBy("legSequence ASC")
    @ToString.Exclude
    private List<Shipment> legs = new ArrayList<>();

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
     * Overall mode label: INTERMODAL when the legs span more than one mode,
     * otherwise the single mode they share (ROAD for an empty itinerary).
     */
    public Shipment.Mode getMode() {
        if (legs == null || legs.isEmpty()) return Shipment.Mode.ROAD;
        Shipment.Mode first = legs.get(0).getMode();
        for (Shipment leg : legs) {
            if (leg.getMode() != first) return Shipment.Mode.INTERMODAL;
        }
        return first != null ? first : Shipment.Mode.ROAD;
    }

    public enum ItineraryStatus { PLANNED, IN_TRANSIT, DELIVERED, CANCELLED }
}
