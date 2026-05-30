package com.driverdirect.dto;

import com.driverdirect.model.Job;
import com.driverdirect.model.JobStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Wire shape stays Job-shaped for backwards compatibility; reads now flow
 * entirely through the Phase-0 TMS tree (Job → Shipment → Order / Stops /
 * Locations) via the navigation getters on Job.
 */
@Data
public class JobResponse {
    private Long id;
    private String title;
    private String description;
    private String pickupLocation;
    private String deliveryLocation;
    // Full ordered route. Empty if the Job hasn't been linked to a Shipment yet.
    private List<StopResponse> stops;
    private Double estimatedDurationHours;
    private LocalDate dateNeeded;
    private BigDecimal ratePerHour;
    private String currency;
    // Pricing (M1b): carrier cost = rate × hours; per-mode platform commission
    // on top; employerTotal = carrierCost + commissionAmount. Read off the leg.
    private BigDecimal carrierCost;
    private BigDecimal commissionPercent;
    private BigDecimal commissionAmount;
    private BigDecimal employerTotal;
    // M3b: the basis the carrier cost was priced on (e.g. PER_CONTAINER × 2).
    private String chargeUnit;
    private BigDecimal chargeableQuantity;
    private String pickupCountry;
    private String deliveryCountry;
    private String requiredLicenceCategory;
    // Transport mode of the underlying Shipment leg (ROAD/RAIL/OCEAN/AIR/…).
    private String transportMode;
    private JobStatus status;
    private String employerCompanyName;
    private Long assignedDriverId;
    private String assignedDriverName;
    private int applicationCount;
    private LocalDateTime createdAt;

    public static JobResponse from(Job job, int applicationCount) {
        JobResponse r = new JobResponse();
        r.setId(job.getId());
        r.setTitle(job.getTitle());
        r.setDescription(job.getDescription());
        r.setPickupLocation(job.getPickupLocation());
        r.setDeliveryLocation(job.getDeliveryLocation());
        r.setStops(job.getShipment() != null && job.getShipment().getStops() != null
                ? job.getShipment().getStops().stream()
                        .map(StopResponse::from)
                        .collect(Collectors.toList())
                : Collections.emptyList());
        r.setEstimatedDurationHours(job.getEstimatedDurationHours());
        r.setDateNeeded(job.getDateNeeded());
        r.setRatePerHour(job.getRatePerHour());
        r.setCurrency(job.getCurrency());
        if (job.getShipment() != null) {
            r.setCarrierCost(job.getShipment().getTotalRate());
            r.setCommissionPercent(job.getShipment().getCommissionPercent());
            r.setCommissionAmount(job.getShipment().getCommissionAmount());
            r.setEmployerTotal(job.getShipment().getEmployerTotal());
            r.setChargeUnit(job.getShipment().getChargeUnit() != null
                    ? job.getShipment().getChargeUnit().name() : null);
            r.setChargeableQuantity(job.getShipment().getChargeableQuantity());
        }
        r.setPickupCountry(job.getPickupCountry());
        r.setDeliveryCountry(job.getDeliveryCountry());
        r.setRequiredLicenceCategory(job.getRequiredLicenceCategory());
        r.setTransportMode(job.getMode() != null ? job.getMode().name() : null);
        r.setStatus(job.getStatus());
        r.setEmployerCompanyName(job.getEmployer().getCompanyName());
        if (job.getAssignedDriver() != null) {
            r.setAssignedDriverId(job.getAssignedDriver().getId());
            r.setAssignedDriverName(job.getAssignedDriver().getFirstName() + " " +
                    job.getAssignedDriver().getLastName());
        }
        r.setApplicationCount(applicationCount);
        r.setCreatedAt(job.getCreatedAt());
        return r;
    }
}
