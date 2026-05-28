package com.driverdirect.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * Per-country exposure snapshot — how many cabotage ops a driver has logged
 * in {@link #country} over the rolling 7-day window. Surfaced on the driver
 * dashboard so the driver can self-assess before they apply.
 */
@Data
public class CabotageExposureResponse {
    private String country;
    private int opsInWindow;
    private int limit;
    private LocalDate windowStart;
    private LocalDate oldestOpDate;
    private LocalDate newestOpDate;
    /** Name of the unload Location for the newest op in this country, when
     *  known (provenance). Null for back-filled ops with no delivery Location. */
    private String newestOpLocation;

    public static CabotageExposureResponse of(String country, int opsInWindow, int limit,
                                              LocalDate windowStart, LocalDate oldestOpDate,
                                              LocalDate newestOpDate, String newestOpLocation) {
        CabotageExposureResponse r = new CabotageExposureResponse();
        r.setCountry(country);
        r.setOpsInWindow(opsInWindow);
        r.setLimit(limit);
        r.setWindowStart(windowStart);
        r.setOldestOpDate(oldestOpDate);
        r.setNewestOpDate(newestOpDate);
        r.setNewestOpLocation(newestOpLocation);
        return r;
    }
}