package com.driverdirect.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Terminal handling for one interchange in an {@link Itinerary}: the cost of
 * getting cargo off leg N and onto leg N+1 at the place where they meet
 * (vessel → rail at a port, truck → aircraft at an airport, or a same-mode
 * transshipment between two sailings at a shared hub).
 *
 * <p>Deliberately <strong>not</strong> a Shipment leg and <strong>not</strong>
 * a Load. A leg is a movement a carrier is assigned to and can apply for; an
 * interchange is stationary work bought from the terminal, so modelling it as
 * a leg would put a phantom "load" in the marketplace for carriers to bid on.
 * It hangs off the Itinerary instead, positioned by {@code afterLegSequence}.
 *
 * <p>Also deliberately <strong>not commissioned</strong>. The platform's
 * commission is defined as a percentage of what a <em>carrier</em> earns
 * ({@code PricingService}); handling is a third-party terminal charge passed
 * through to the shipper at cost. This also keeps the routing engine's quote
 * reconcilable with the bill, since {@code RoutePlanner} likewise adds transfer
 * cost raw.
 *
 * <p>Amounts are derived, never client-supplied: {@code PricingService}
 * recomputes the whole set from {@code TransferPolicy} whenever an itinerary
 * is priced, so they cannot drift from the rates the route planner quoted.
 */
@Entity
@Table(name = "handling_charges",
        indexes = @Index(name = "idx_handling_charges_itinerary", columnList = "itinerary_id"))
@Data
@NoArgsConstructor
public class HandlingCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "itinerary_id", nullable = false)
    private Itinerary itinerary;

    /** Where the interchange happens — the location both legs share. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private Location location;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_mode")
    private Shipment.Mode fromMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_mode")
    private Shipment.Mode toMode;

    /** The legSequence of the leg this interchange follows, so the charges
     *  order alongside the legs they sit between. */
    @Column(name = "after_leg_sequence", nullable = false)
    private Integer afterLegSequence;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(length = 3, nullable = false)
    private String currency = "EUR";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
