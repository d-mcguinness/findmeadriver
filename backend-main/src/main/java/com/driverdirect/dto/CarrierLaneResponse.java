package com.driverdirect.dto;

import com.driverdirect.model.CarrierLane;
import lombok.Data;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Data
public class CarrierLaneResponse {
    private Long id;
    private String originCountry;
    private String destinationCountry;
    private LocalDateTime createdAt;

    // ---- Timetable (all null on a plain country-pair lane) ----
    private boolean timetabled;
    private String serviceMode;
    private Long originLocationId;
    private String originLocationName;
    private Long destinationLocationId;
    private String destinationLocationName;
    private List<String> departureDays;
    private LocalTime departureTime;
    private Double transitDurationHours;

    public static CarrierLaneResponse from(CarrierLane l) {
        CarrierLaneResponse r = new CarrierLaneResponse();
        r.setId(l.getId());
        r.setOriginCountry(l.getOriginCountry());
        r.setDestinationCountry(l.getDestinationCountry());
        r.setCreatedAt(l.getCreatedAt());
        r.setTimetabled(l.isTimetabled());
        r.setServiceMode(l.getServiceMode() != null ? l.getServiceMode().name() : null);
        if (l.getOriginLocation() != null) {
            r.setOriginLocationId(l.getOriginLocation().getId());
            r.setOriginLocationName(l.getOriginLocation().getName());
        }
        if (l.getDestinationLocation() != null) {
            r.setDestinationLocationId(l.getDestinationLocation().getId());
            r.setDestinationLocationName(l.getDestinationLocation().getName());
        }
        if (l.isTimetabled()) {
            r.setDepartureDays(l.getDepartureDaySet().stream().map(DayOfWeek::name).sorted().toList());
            r.setDepartureTime(l.getDepartureTime());
            r.setTransitDurationHours(l.getTransitDurationHours());
        }
        return r;
    }
}
