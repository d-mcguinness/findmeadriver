package com.driverdirect.dto;

import com.driverdirect.model.ApplicationStatus;
import com.driverdirect.model.LoadApplication;
import com.driverdirect.model.LoadStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LoadApplicationResponse {
    private Long id;
    private Long loadId;
    private String loadTitle;
    private LoadStatus loadStatus;
    private String carrierName;
    private String carrierEmail;
    private Long carrierId;
    private ApplicationStatus status;
    private String coverNote;
    private LocalDateTime appliedAt;
    private Double carrierAverageRating;
    private Long carrierRatingCount;
    private boolean carrierVerified;

    public static LoadApplicationResponse from(LoadApplication app) {
        return from(app, null, null, false);
    }

    public static LoadApplicationResponse from(LoadApplication app, Double avgRating, Long ratingCount, boolean verified) {
        LoadApplicationResponse r = new LoadApplicationResponse();
        r.setId(app.getId());
        r.setLoadId(app.getLoad().getId());
        r.setLoadTitle(app.getLoad().getTitle());
        r.setLoadStatus(app.getLoad().getStatus());
        r.setCarrierName(app.getCarrier().getFirstName() + " " + app.getCarrier().getLastName());
        r.setCarrierEmail(app.getCarrier().getEmail());
        r.setCarrierId(app.getCarrier().getId());
        r.setStatus(app.getStatus());
        r.setCoverNote(app.getCoverNote());
        r.setAppliedAt(app.getAppliedAt());
        r.setCarrierAverageRating(avgRating);
        r.setCarrierRatingCount(ratingCount);
        r.setCarrierVerified(verified);
        return r;
    }
}
