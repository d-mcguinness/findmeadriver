package com.driverdirect.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class CreateJobRequest {
    private String title;
    private String description;
    private String pickupLocation;
    private String deliveryLocation;
    private Double estimatedDurationHours;
    private LocalDate dateNeeded;
    private BigDecimal ratePerHour;
    // All optional; backend falls back to the employer's defaults.
    private String currency;
    private String pickupCountry;
    private String deliveryCountry;
    private String requiredLicenceCategory;
    /**
     * Full ordered route. When non-empty, the tree builder uses this list and
     * ignores {@link #pickupLocation}/{@link #deliveryLocation}. Legacy
     * clients that omit it keep working — first PICKUP / last DELIVERY are
     * synthesised from the legacy fields.
     */
    private List<CreateJobStopRequest> stops;
}
