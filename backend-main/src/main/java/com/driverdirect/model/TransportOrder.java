package com.driverdirect.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * The customer's transport request — what they want delivered. Decoupled
 * from "how it physically moves" (Shipment) and "who carries it" (Load).
 *
 * Named TransportOrder rather than Order because ORDER is a SQL reserved word
 * in many dialects; the table is "transport_orders".
 */
@Entity
@Table(name = "transport_orders")
@Data
@NoArgsConstructor
public class TransportOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipper_id", nullable = false)
    private Shipper shipper;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "customer_reference")
    private String customerReference; // customer PO / WO number

    @NotBlank
    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_level", nullable = false)
    private ServiceLevel serviceLevel = ServiceLevel.STANDARD;

    // dateNeeded stays the one required, authoritative date for every existing
    // consumer (single-leg + shipper-authored intermodal creation, sorting,
    // display) — nothing about its meaning changes. The three fields below are
    // optional richer context from a flexible-handover routing search (see
    // README.md, "Proposed: multimodal routing engine"): when present, they
    // describe the window a RouteQuery searched, and dateNeeded is set to
    // whichever handover date the accepted RouteOption actually used. All
    // three are null for every order created the normal way today.
    @Column(name = "date_needed")
    private LocalDate dateNeeded;

    /** Earliest the shipper can hand cargo over — the start of the window a
     *  routing search explored. Null unless this order came from one. */
    @Column(name = "earliest_ready_date")
    private LocalDate earliestReadyDate;

    /** Latest acceptable handover date — the end of the window a routing
     *  search explored. Null unless this order came from one. */
    @Column(name = "latest_handover_date")
    private LocalDate latestHandoverDate;

    /** The hard arrival deadline a routing search filtered on (never an
     *  optimisation criterion — see README.md). Null unless this order came
     *  from one; distinct from {@code dateNeeded}, which is a handover date. */
    @Column(name = "arrival_deadline")
    private LocalDate arrivalDeadline;

    @Column(length = 3, nullable = false)
    private String currency = "EUR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.NEW;

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

    public enum ServiceLevel { STANDARD, EXPRESS, ECONOMY, TIME_DEFINITE }
    public enum OrderStatus { NEW, PLANNED, IN_EXECUTION, COMPLETED, CANCELLED }
}
