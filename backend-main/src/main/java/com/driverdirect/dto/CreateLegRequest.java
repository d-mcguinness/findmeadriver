package com.driverdirect.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * One leg of an intermodal movement (M2b). The transportMode is optional and
 * defaults to ROAD when null/unrecognised; pickup/delivery/rate/duration are
 * required (validated in the service).
 */
@Data
public class CreateLegRequest {
    private String transportMode;
    private String pickupLocation;
    private String deliveryLocation;
    private String pickupCountry;
    private String deliveryCountry;
    // Endpoint Location ids — optional, and authoritative when present: the
    // leg's Stop binds to exactly this row instead of being re-derived from
    // pickupLocation/pickupCountry by name. The routing engine sets them when
    // a proposed route is accepted so the Stop keeps the typed terminal the
    // planner actually routed through (coordinates, timezone, UN/LOCODE) — a
    // name lookup can miss and mint a duplicate untyped ADDRESS. A client that
    // sends only names behaves exactly as before. An id the caller may not
    // reference is rejected like an unknown one (Location.isAccessibleBy).
    private Long pickupLocationId;
    private Long deliveryLocationId;
    private BigDecimal ratePerHour;
    private Double estimatedDurationHours;
    private String requiredLicenceCategory;
    // Per-mode pricing inputs (M3b); optional. When present, the leg is priced
    // on its mode's basis (km / containers / chargeable-kg / pieces) instead of
    // rate × hours.
    private BigDecimal distanceKm;
    // How distanceKm was arrived at (Shipment.DistanceSource name). Set to
    // GREAT_CIRCLE_ESTIMATE by the routing engine, whose distances are modelled
    // from endpoint coordinates rather than measured along a path. Leave null
    // when posting a distance of your own — it records as CLIENT_SUPPLIED.
    private String distanceSource;
    private BigDecimal weightKg;
    private BigDecimal volumeM3;
    private Integer containerCount;
    private Integer pieceCount;
}
