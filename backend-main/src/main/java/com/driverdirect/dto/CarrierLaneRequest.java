package com.driverdirect.dto;

import lombok.Data;

import java.time.LocalTime;
import java.util.List;

@Data
public class CarrierLaneRequest {
    private String originCountry;
    private String destinationCountry;

    // ---- Optional timetable (routing-engine step 2) ----
    // Either supply all three of departureDays/departureTime/transitDurationHours
    // (a scheduled service) or none of them (a plain country-pair lane, the
    // pre-existing behaviour). Partial timetables are rejected in the service.

    /** RAIL / OCEAN / AIR — a scheduled service's mode. */
    private String serviceMode;
    /** Optional typed-terminal anchors (Location ids). */
    private Long originLocationId;
    private Long destinationLocationId;
    /** DayOfWeek names, e.g. ["MONDAY","THURSDAY"]. */
    private List<String> departureDays;
    private LocalTime departureTime;
    private Double transitDurationHours;
}
