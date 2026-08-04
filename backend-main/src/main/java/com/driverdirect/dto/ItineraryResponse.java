package com.driverdirect.dto;

import com.driverdirect.model.HandlingCharge;
import com.driverdirect.model.Itinerary;
import com.driverdirect.model.Load;
import com.driverdirect.model.Location;
import com.driverdirect.model.Shipment;
import com.driverdirect.model.Stop;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Read view of an intermodal movement (M2): the rolled-up totals plus an
 * ordered summary of each leg. Mapped via the house static-factory pattern.
 */
@Data
public class ItineraryResponse {
    private Long id;
    private Long shipperId;
    private String shipperName;
    private Long orderId;
    private String orderTitle;
    // Order-level metadata (M2c) — surfaced so the edit form can prefill,
    // mirroring LoadResponse's description/dateNeeded.
    private String description;
    private LocalDate dateNeeded;
    // Optional flexible-window context from a routing search — surfaced for
    // completeness; dateNeeded above stays the authoritative single date.
    private LocalDate earliestReadyDate;
    private LocalDate latestHandoverDate;
    private LocalDate arrivalDeadline;
    private String status;
    private String mode; // INTERMODAL when legs span >1 mode, else the shared mode
    private String currency;
    private BigDecimal carrierCostTotal;
    private BigDecimal commissionTotal;
    /** Terminal handling across the interchanges between legs — see
     *  {@link #handling} for the per-interchange breakdown. Included in
     *  grandTotal; uncommissioned (a pass-through terminal charge). */
    private BigDecimal handlingTotal;
    /** carrierCostTotal + commissionTotal + handlingTotal. */
    private BigDecimal grandTotal;
    private String originCountry;
    private String destinationCountry;
    private int legCount;
    private List<LegSummary> legs;
    /** One entry per interchange, ordered by the leg it follows. Empty for a
     *  single-leg or road-through itinerary (road→road needs no interchange). */
    private List<HandlingSummary> handling = List.of();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** One terminal interchange the shipper is charged for. */
    @Data
    public static class HandlingSummary {
        private Long id;
        /** legSequence of the leg this interchange follows. */
        private Integer afterLegSequence;
        private String locationName;
        private String locationType;
        private String fromMode;
        private String toMode;
        private BigDecimal amount;
        private String currency;

        static HandlingSummary from(HandlingCharge c) {
            HandlingSummary h = new HandlingSummary();
            h.id = c.getId();
            h.afterLegSequence = c.getAfterLegSequence();
            if (c.getLocation() != null) {
                h.locationName = c.getLocation().getName();
                h.locationType = c.getLocation().getLocationType() != null
                        ? c.getLocation().getLocationType().name() : null;
            }
            h.fromMode = c.getFromMode() != null ? c.getFromMode().name() : null;
            h.toMode = c.getToMode() != null ? c.getToMode().name() : null;
            h.amount = c.getAmount();
            h.currency = c.getCurrency();
            return h;
        }
    }

    @Data
    public static class LegSummary {
        private Long shipmentId;
        private Integer legSequence;
        private String mode;
        private String status;
        private String originCountry;
        private String destinationCountry;
        private String pickupLocation;
        private String deliveryLocation;
        private String pickupLocationType;
        private String pickupCode;
        private String deliveryLocationType;
        private String deliveryCode;
        private String currency;
        private String chargeUnit;
        private BigDecimal chargeableQuantity;
        private BigDecimal carrierCost;
        private BigDecimal commissionPercent;
        private BigDecimal commissionAmount;
        private BigDecimal shipperTotal;
        // Per-mode pricing quantities + the carrier-assignment fields that live
        // on the leg's Load rather than its Shipment — surfaced (Load present)
        // so the edit form can prefill, mirroring LoadResponse.
        private BigDecimal distanceKm;
        /** Basis for distanceKm — see Shipment.DistanceSource. An accepted route's
         *  legs are GREAT_CIRCLE_ESTIMATE. */
        private String distanceSource;
        private BigDecimal weightKg;
        private BigDecimal volumeM3;
        private Integer containerCount;
        private Integer pieceCount;
        private String requiredLicenceCategory;
        private BigDecimal ratePerHour;
        private Double estimatedDurationHours;

        static LegSummary from(Shipment s) {
            return from(s, null);
        }

        static LegSummary from(Shipment s, Load load) {
            LegSummary l = new LegSummary();
            l.shipmentId = s.getId();
            l.legSequence = s.getLegSequence();
            l.mode = s.getMode() != null ? s.getMode().name() : null;
            l.status = s.getStatus() != null ? s.getStatus().name() : null;
            l.originCountry = s.getOriginCountry();
            l.destinationCountry = s.getDestinationCountry();
            Location pu = stopLocation(s, Stop.StopType.PICKUP, true);
            if (pu != null) {
                l.pickupLocation = pu.getName();
                l.pickupLocationType = pu.getLocationType() != null ? pu.getLocationType().name() : null;
                l.pickupCode = nodeCode(pu);
            }
            Location de = stopLocation(s, Stop.StopType.DELIVERY, false);
            if (de != null) {
                l.deliveryLocation = de.getName();
                l.deliveryLocationType = de.getLocationType() != null ? de.getLocationType().name() : null;
                l.deliveryCode = nodeCode(de);
            }
            l.currency = s.getCurrency();
            l.chargeUnit = s.getChargeUnit() != null ? s.getChargeUnit().name() : null;
            l.chargeableQuantity = s.getChargeableQuantity();
            l.carrierCost = s.getTotalRate();
            l.commissionPercent = s.getCommissionPercent();
            l.commissionAmount = s.getCommissionAmount();
            l.shipperTotal = s.getShipperTotal();
            l.distanceKm = s.getDistanceKm();
            l.distanceSource = s.getDistanceSource() != null ? s.getDistanceSource().name() : null;
            l.weightKg = s.getWeightKg();
            l.volumeM3 = s.getVolumeM3();
            l.containerCount = s.getContainerCount();
            l.pieceCount = s.getPieceCount();
            if (load != null) {
                l.requiredLicenceCategory = load.getRequiredLicenceCategory();
                l.ratePerHour = load.getRatePerHour();
                l.estimatedDurationHours = load.getEstimatedDurationHours();
            }
            return l;
        }

        /** First (or last) stop of the given type on the leg, as a Location. */
        private static Location stopLocation(Shipment s, Stop.StopType type, boolean first) {
            if (s.getStops() == null) return null;
            Location loc = null;
            for (Stop stop : s.getStops()) {
                if (stop.getType() == type && stop.getLocation() != null) {
                    loc = stop.getLocation();
                    if (first) break;
                }
            }
            return loc;
        }

        /** UN/LOCODE if present, else IATA — the node's identifying code. */
        private static String nodeCode(Location loc) {
            if (loc.getUnlocode() != null && !loc.getUnlocode().isBlank()) return loc.getUnlocode();
            return loc.getIata();
        }
    }

    public static ItineraryResponse from(Itinerary it) {
        return from(it, Map.of());
    }

    /** @param loadsByShipmentId each leg's carrier-assignment Load, keyed by
     *  its Shipment id — enriches every {@link LegSummary} with the fields
     *  that live on Load (rate/hours/licence) for the edit form. Pass
     *  {@code Map.of()} (via {@link #from(Itinerary)}) where that detail
     *  isn't needed, e.g. list views. */
    public static ItineraryResponse from(Itinerary it, Map<Long, Load> loadsByShipmentId) {
        ItineraryResponse r = new ItineraryResponse();
        r.setId(it.getId());
        if (it.getShipper() != null) {
            r.setShipperId(it.getShipper().getId());
            r.setShipperName(it.getShipper().getCompanyName());
        }
        if (it.getOrder() != null) {
            r.setOrderId(it.getOrder().getId());
            r.setOrderTitle(it.getOrder().getTitle());
            r.setDescription(it.getOrder().getDescription());
            r.setDateNeeded(it.getOrder().getDateNeeded());
            r.setEarliestReadyDate(it.getOrder().getEarliestReadyDate());
            r.setLatestHandoverDate(it.getOrder().getLatestHandoverDate());
            r.setArrivalDeadline(it.getOrder().getArrivalDeadline());
        }
        r.setStatus(it.getStatus() != null ? it.getStatus().name() : null);
        r.setMode(it.getMode() != null ? it.getMode().name() : null);
        r.setCurrency(it.getCurrency());
        r.setCarrierCostTotal(it.getCarrierCostTotal());
        r.setCommissionTotal(it.getCommissionTotal());
        r.setHandlingTotal(it.getHandlingTotal());
        r.setGrandTotal(it.getGrandTotal());
        r.setOriginCountry(it.getOriginCountry());
        r.setDestinationCountry(it.getDestinationCountry());
        r.setCreatedAt(it.getCreatedAt());
        r.setUpdatedAt(it.getUpdatedAt());
        List<Shipment> legs = it.getLegs() != null ? it.getLegs() : List.of();
        r.setLegCount(legs.size());
        r.setLegs(legs.stream().map(s -> LegSummary.from(s, loadsByShipmentId.get(s.getId()))).toList());
        List<HandlingCharge> charges =
                it.getHandlingCharges() != null ? it.getHandlingCharges() : List.<HandlingCharge>of();
        r.setHandling(charges.stream().map(HandlingSummary::from).toList());
        return r;
    }
}
