package com.driverdirect.dto;

import com.driverdirect.model.Load;
import com.driverdirect.model.LoadStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Wire shape stays Load-shaped for backwards compatibility; reads now flow
 * entirely through the Phase-0 TMS tree (Load → Shipment → Order / Stops /
 * Locations) via the navigation getters on Load.
 */
@Data
public class LoadResponse {
    private Long id;
    private String title;
    private String description;
    private String pickupLocation;
    private String deliveryLocation;
    // Full ordered route. Empty if the Load hasn't been linked to a Shipment yet.
    private List<StopResponse> stops;
    private Double estimatedDurationHours;
    private LocalDate dateNeeded;
    // Optional flexible-window context from a routing search — surfaced for
    // completeness; dateNeeded above stays the authoritative single date.
    private LocalDate earliestReadyDate;
    private LocalDate latestHandoverDate;
    private LocalDate arrivalDeadline;
    private BigDecimal ratePerHour;
    private String currency;
    // Pricing (M1b): carrier cost = rate × hours; per-mode platform commission
    // on top; shipperTotal = carrierCost + commissionAmount. Read off the leg.
    private BigDecimal carrierCost;
    private BigDecimal commissionPercent;
    private BigDecimal commissionAmount;
    private BigDecimal shipperTotal;
    // M3b: the basis the carrier cost was priced on (e.g. PER_CONTAINER × 2).
    private String chargeUnit;
    private BigDecimal chargeableQuantity;
    // Per-mode pricing quantities (M3b) — surfaced so the edit form can prefill.
    private BigDecimal distanceKm;
    private BigDecimal weightKg;
    private BigDecimal volumeM3;
    private Integer containerCount;
    private Integer pieceCount;
    private String pickupCountry;
    private String deliveryCountry;
    // International / domestic / unknown classification of the leg (cabotage context).
    private String movementType;
    private String requiredLicenceCategory;
    // Transport mode of the underlying Shipment leg (ROAD/RAIL/OCEAN/AIR/…).
    private String transportMode;
    private LoadStatus status;
    private String shipperCompanyName;
    private Long assignedCarrierId;
    private String assignedCarrierName;
    private int applicationCount;
    private LocalDateTime createdAt;

    public static LoadResponse from(Load load, int applicationCount) {
        LoadResponse r = new LoadResponse();
        r.setId(load.getId());
        r.setTitle(load.getTitle());
        r.setDescription(load.getDescription());
        r.setPickupLocation(load.getPickupLocation());
        r.setDeliveryLocation(load.getDeliveryLocation());
        r.setStops(load.getShipment() != null && load.getShipment().getStops() != null
                ? load.getShipment().getStops().stream()
                        .map(StopResponse::from)
                        .collect(Collectors.toList())
                : Collections.emptyList());
        r.setEstimatedDurationHours(load.getEstimatedDurationHours());
        r.setDateNeeded(load.getDateNeeded());
        r.setEarliestReadyDate(load.getEarliestReadyDate());
        r.setLatestHandoverDate(load.getLatestHandoverDate());
        r.setArrivalDeadline(load.getArrivalDeadline());
        r.setRatePerHour(load.getRatePerHour());
        r.setCurrency(load.getCurrency());
        if (load.getShipment() != null) {
            r.setCarrierCost(load.getShipment().getTotalRate());
            r.setCommissionPercent(load.getShipment().getCommissionPercent());
            r.setCommissionAmount(load.getShipment().getCommissionAmount());
            r.setShipperTotal(load.getShipment().getShipperTotal());
            r.setChargeUnit(load.getShipment().getChargeUnit() != null
                    ? load.getShipment().getChargeUnit().name() : null);
            r.setChargeableQuantity(load.getShipment().getChargeableQuantity());
            r.setDistanceKm(load.getShipment().getDistanceKm());
            r.setWeightKg(load.getShipment().getWeightKg());
            r.setVolumeM3(load.getShipment().getVolumeM3());
            r.setContainerCount(load.getShipment().getContainerCount());
            r.setPieceCount(load.getShipment().getPieceCount());
        }
        r.setPickupCountry(load.getPickupCountry());
        r.setDeliveryCountry(load.getDeliveryCountry());
        r.setMovementType(load.getMovementType().name());
        r.setRequiredLicenceCategory(load.getRequiredLicenceCategory());
        r.setTransportMode(load.getMode() != null ? load.getMode().name() : null);
        r.setStatus(load.getStatus());
        r.setShipperCompanyName(load.getShipper().getCompanyName());
        if (load.getAssignedCarrier() != null) {
            r.setAssignedCarrierId(load.getAssignedCarrier().getId());
            r.setAssignedCarrierName(load.getAssignedCarrier().getFirstName() + " " +
                    load.getAssignedCarrier().getLastName());
        }
        r.setApplicationCount(applicationCount);
        r.setCreatedAt(load.getCreatedAt());
        return r;
    }
}
