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
    private BigDecimal ratePerHour;
    private Double estimatedDurationHours;
    private String requiredLicenceCategory;
    // Per-mode pricing inputs (M3b); optional. When present, the leg is priced
    // on its mode's basis (km / containers / chargeable-kg / pieces) instead of
    // rate × hours.
    private BigDecimal distanceKm;
    private BigDecimal weightKg;
    private BigDecimal volumeM3;
    private Integer containerCount;
    private Integer pieceCount;
}
