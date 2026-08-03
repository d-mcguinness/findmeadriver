package com.driverdirect.dto;

import com.driverdirect.model.Shipment;
import com.driverdirect.model.Stop;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ShipmentResponse {
    private Long id;
    private Long shipperId;
    private String shipperName;
    // Intermodal leg membership (M2): null for standalone single-leg shipments.
    private Long itineraryId;
    private Integer legSequence;
    private String mode;
    private String status;
    private String currency;
    private BigDecimal totalRate;
    private BigDecimal commissionPercent;
    private BigDecimal commissionAmount;
    private BigDecimal shipperTotal;
    private String chargeUnit;
    private BigDecimal chargeableQuantity;
    private BigDecimal distanceKm;
    /** How distanceKm was arrived at — CLIENT_SUPPLIED (measured/stated on the
     *  request) or GREAT_CIRCLE_ESTIMATE (modelled by the routing engine from
     *  endpoint coordinates). Null on legacy rows and legs with no distance. */
    private String distanceSource;
    private BigDecimal weightKg;
    private BigDecimal volumeM3;
    private Integer containerCount;
    private Integer pieceCount;
    private LocalDateTime tenderedAt;
    private LocalDateTime acceptedAt;
    private String originCountry;
    private String destinationCountry;
    private List<StopSummary> stops;
    private List<LineSummary> lines;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    public static class StopSummary {
        private Long id;
        private int sequence;
        private String type;
        private Long locationId;
        private String locationName;
        private String locationCountry;
        private LocalDateTime earliestAt;
        private LocalDateTime latestAt;
        private LocalDateTime actualAt;

        static StopSummary from(Stop s) {
            StopSummary x = new StopSummary();
            x.id = s.getId();
            x.sequence = s.getSequence();
            x.type = s.getType() != null ? s.getType().name() : null;
            if (s.getLocation() != null) {
                x.locationId = s.getLocation().getId();
                x.locationName = s.getLocation().getName();
                x.locationCountry = s.getLocation().getCountry();
            }
            x.earliestAt = s.getEarliestAt();
            x.latestAt = s.getLatestAt();
            x.actualAt = s.getActualAt();
            return x;
        }
    }

    @Data
    public static class LineSummary {
        private Long id;
        private Long orderId;
        private String orderTitle;
    }

    public static ShipmentResponse from(Shipment s) {
        ShipmentResponse r = new ShipmentResponse();
        r.setId(s.getId());
        r.setShipperId(s.getShipper().getId());
        r.setShipperName(s.getShipper().getCompanyName());
        if (s.getItinerary() != null) {
            r.setItineraryId(s.getItinerary().getId());
        }
        r.setLegSequence(s.getLegSequence());
        r.setMode(s.getMode() != null ? s.getMode().name() : null);
        r.setStatus(s.getStatus() != null ? s.getStatus().name() : null);
        r.setCurrency(s.getCurrency());
        r.setTotalRate(s.getTotalRate());
        r.setCommissionPercent(s.getCommissionPercent());
        r.setCommissionAmount(s.getCommissionAmount());
        r.setShipperTotal(s.getShipperTotal());
        r.setChargeUnit(s.getChargeUnit() != null ? s.getChargeUnit().name() : null);
        r.setChargeableQuantity(s.getChargeableQuantity());
        r.setDistanceKm(s.getDistanceKm());
        r.setDistanceSource(s.getDistanceSource() != null ? s.getDistanceSource().name() : null);
        r.setWeightKg(s.getWeightKg());
        r.setVolumeM3(s.getVolumeM3());
        r.setContainerCount(s.getContainerCount());
        r.setPieceCount(s.getPieceCount());
        r.setTenderedAt(s.getTenderedAt());
        r.setAcceptedAt(s.getAcceptedAt());
        r.setOriginCountry(s.getOriginCountry());
        r.setDestinationCountry(s.getDestinationCountry());
        r.setCreatedAt(s.getCreatedAt());
        r.setUpdatedAt(s.getUpdatedAt());
        if (s.getStops() != null) {
            r.setStops(s.getStops().stream().map(StopSummary::from).toList());
        }
        if (s.getShipmentLines() != null) {
            r.setLines(s.getShipmentLines().stream().map(sl -> {
                LineSummary l = new LineSummary();
                l.setId(sl.getId());
                if (sl.getOrder() != null) {
                    l.setOrderId(sl.getOrder().getId());
                    l.setOrderTitle(sl.getOrder().getTitle());
                }
                return l;
            }).toList());
        }
        return r;
    }
}
