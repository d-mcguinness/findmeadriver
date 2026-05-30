package com.driverdirect.dto;

import com.driverdirect.model.Itinerary;
import com.driverdirect.model.Location;
import com.driverdirect.model.Shipment;
import com.driverdirect.model.Stop;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Read view of an intermodal movement (M2): the rolled-up totals plus an
 * ordered summary of each leg. Mapped via the house static-factory pattern.
 */
@Data
public class ItineraryResponse {
    private Long id;
    private Long employerId;
    private String employerName;
    private Long orderId;
    private String orderTitle;
    private String status;
    private String mode; // INTERMODAL when legs span >1 mode, else the shared mode
    private String currency;
    private BigDecimal carrierCostTotal;
    private BigDecimal commissionTotal;
    private BigDecimal grandTotal;
    private String originCountry;
    private String destinationCountry;
    private int legCount;
    private List<LegSummary> legs;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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
        private BigDecimal employerTotal;

        static LegSummary from(Shipment s) {
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
            l.employerTotal = s.getEmployerTotal();
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
        ItineraryResponse r = new ItineraryResponse();
        r.setId(it.getId());
        if (it.getEmployer() != null) {
            r.setEmployerId(it.getEmployer().getId());
            r.setEmployerName(it.getEmployer().getCompanyName());
        }
        if (it.getOrder() != null) {
            r.setOrderId(it.getOrder().getId());
            r.setOrderTitle(it.getOrder().getTitle());
        }
        r.setStatus(it.getStatus() != null ? it.getStatus().name() : null);
        r.setMode(it.getMode() != null ? it.getMode().name() : null);
        r.setCurrency(it.getCurrency());
        r.setCarrierCostTotal(it.getCarrierCostTotal());
        r.setCommissionTotal(it.getCommissionTotal());
        r.setGrandTotal(it.getGrandTotal());
        r.setOriginCountry(it.getOriginCountry());
        r.setDestinationCountry(it.getDestinationCountry());
        r.setCreatedAt(it.getCreatedAt());
        r.setUpdatedAt(it.getUpdatedAt());
        List<Shipment> legs = it.getLegs() != null ? it.getLegs() : List.of();
        r.setLegCount(legs.size());
        r.setLegs(legs.stream().map(LegSummary::from).toList());
        return r;
    }
}
