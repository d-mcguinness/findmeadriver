package com.driverdirect.dto;

import lombok.Data;

import java.util.List;

/**
 * Bundle of everything the carrier dashboard needs to render the cabotage
 * compliance section: the carrier's declared home country (nullable) and the
 * per-country exposure rows for the current 7-day window.
 */
@Data
public class CabotageDashboardResponse {
    private String homeCountry;
    private List<CabotageExposureResponse> exposures;

    public static CabotageDashboardResponse of(String homeCountry,
                                               List<CabotageExposureResponse> exposures) {
        CabotageDashboardResponse r = new CabotageDashboardResponse();
        r.setHomeCountry(homeCountry);
        r.setExposures(exposures);
        return r;
    }
}
