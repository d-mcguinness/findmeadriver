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
    private Long employerId;
    private String employerName;
    private String mode;
    private String status;
    private String currency;
    private BigDecimal totalRate;
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
        r.setEmployerId(s.getEmployer().getId());
        r.setEmployerName(s.getEmployer().getCompanyName());
        r.setMode(s.getMode() != null ? s.getMode().name() : null);
        r.setStatus(s.getStatus() != null ? s.getStatus().name() : null);
        r.setCurrency(s.getCurrency());
        r.setTotalRate(s.getTotalRate());
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
