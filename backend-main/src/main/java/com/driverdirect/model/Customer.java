package com.driverdirect.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;

/**
 * The buyer of transport, distinct from the platform Shipper account that
 * runs this Customer's orders. In v1 every Shipper gets one auto-created
 * "(default)" Customer so existing flows keep working.
 */
@Entity
@Table(name = "customers")
@Data
@NoArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipper_id", nullable = false)
    private Shipper shipper;

    @NotBlank
    private String name;

    @Column(length = 2, nullable = false)
    private String country = "IE";

    @Column(length = 3, nullable = false)
    private String currency = "EUR";

    @Column(length = 256, name = "billing_address")
    private String billingAddress;

    public Customer(Shipper shipper, String name) {
        this.shipper = shipper;
        this.name = name;
        this.country = shipper.getCountry();
        this.currency = shipper.getCurrency();
    }
}
