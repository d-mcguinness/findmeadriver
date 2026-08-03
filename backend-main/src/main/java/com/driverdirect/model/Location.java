package com.driverdirect.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;

/**
 * First-class address record. Replaces the free-text pickup/delivery strings
 * on Load. Owned by an Shipper when curated (warehouse, customer DC); null
 * owner means ad-hoc (typed once on the post-a-load form).
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
    @JoinColumn(name = "shipper_id")
    private Shipper ownerShipper; // null = ad-hoc

    @NotBlank
    private String name;

    @NotBlank
    @Column(name = "address_line")
    private String addressLine;

    private String city;

    @Column(length = 2, nullable = false)
    private String country = "IE";

    // Geography typing (M3): a plain street ADDRESS by default, or a named node
    // (port / airport / rail terminal). unlocode (UN/LOCODE, e.g. IEDUB, NLRTM)
    // identifies sea/inland nodes; iata (e.g. ORK, CDG) identifies airports.
    // All additive — existing rows stay ADDRESS with null codes.
    @Enumerated(EnumType.STRING)
    @Column(name = "location_type", nullable = false)
    private LocationType locationType = LocationType.ADDRESS;

    @Column(length = 5)
    private String unlocode;

    @Column(length = 3)
    private String iata;

    private Double latitude;
    private Double longitude;

    @Column(length = 64)
    private String timezone; // IANA name, e.g. "Europe/Dublin"

    @Column(length = 1024, name = "operating_hours")
    private String operatingHours; // "MO-FR 08:00-18:00" — parsed by a later phase

    /**
     * May {@code shipper} reference this location by id? A typed reference node
     * (port, airport, rail/inland terminal) is public infrastructure any tenant
     * may route through or post against; a plain ADDRESS is private to the
     * shipper that curated it, and an ad-hoc one (null owner) belongs to nobody.
     *
     * <p>Callers must report a failure exactly the way they report an unknown
     * id — never reveal that the row exists or what it is called.
     */
    public boolean isAccessibleBy(Shipper shipper) {
        if (locationType != null && locationType != LocationType.ADDRESS) return true;
        return shipper != null && ownerShipper != null
                && ownerShipper.getId() != null
                && ownerShipper.getId().equals(shipper.getId());
    }

    public enum LocationType { ADDRESS, SEAPORT, AIRPORT, RAIL_TERMINAL, INLAND_TERMINAL }
}
