package com.driverdirect.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;

/**
 * First-class address record. Replaces the free-text pickup/delivery strings
 * on Job. Owned by an Employer when curated (warehouse, customer DC); null
 * owner means ad-hoc (typed once on the post-a-job form).
 */
@Entity
@Table(name = "locations")
@Data
@NoArgsConstructor
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employer_id")
    private Employer ownerEmployer; // null = ad-hoc

    @NotBlank
    private String name;

    @NotBlank
    @Column(name = "address_line")
    private String addressLine;

    private String city;

    @Column(length = 2, nullable = false)
    private String country = "IE";

    private Double latitude;
    private Double longitude;

    @Column(length = 64)
    private String timezone; // IANA name, e.g. "Europe/Dublin"

    @Column(length = 1024, name = "operating_hours")
    private String operatingHours; // "MO-FR 08:00-18:00" — parsed by a later phase
}
