package com.driverdirect.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Many-to-many linker: a Shipment can carry several Orders (consolidation),
 * and an Order can split across several Shipments (multi-leg). V1 enforces
 * 1:1 via the unique constraint — drop it when consolidation/splitting lands.
 */
@Entity
@Table(name = "shipment_lines",
        uniqueConstraints = @UniqueConstraint(columnNames = {"shipment_id", "order_id"}))
@Data
@NoArgsConstructor
public class ShipmentLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false)
    private Shipment shipment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private TransportOrder order;

    @Column(length = 512)
    private String notes;
}
