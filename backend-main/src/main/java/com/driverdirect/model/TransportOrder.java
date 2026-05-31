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

    @Column(name = "date_needed")
    private LocalDate dateNeeded;

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
