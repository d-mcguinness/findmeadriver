package com.driverdirect.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;

/**
 * Line-level cargo on a TransportOrder. Optional in v1; required once you
 * want weight/cube-based load planning or hazmat classification.
 */
@Entity
@Table(name = "order_items")
@Data
@NoArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private TransportOrder order;

    @NotBlank
    private String description;

    private Integer quantity;

    @Column(name = "weight_kg")
    private Double weightKg;

    @Column(name = "volume_m3")
    private Double volumeM3;

    @Column(nullable = false)
    private boolean hazmat = false;

    @Column(length = 32, name = "un_number")
    private String unNumber; // hazmat UN classification

    @Column(name = "temperature_min_c")
    private Double temperatureMinC;

    @Column(name = "temperature_max_c")
    private Double temperatureMaxC;
}