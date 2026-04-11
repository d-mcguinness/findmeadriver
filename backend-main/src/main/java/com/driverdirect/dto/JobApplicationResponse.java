package com.driverdirect.dto;

import com.driverdirect.model.ApplicationStatus;
import com.driverdirect.model.JobApplication;
import com.driverdirect.model.JobStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class JobApplicationResponse {
    private Long id;
    private Long jobId;
    private String jobTitle;
    private JobStatus jobStatus;
    private String driverName;
    private String driverEmail;
    private Long driverId;
    private ApplicationStatus status;
    private String coverNote;
    private LocalDateTime appliedAt;
    private Double driverAverageRating;
    private Long driverRatingCount;
    private boolean driverVerified;

    public static JobApplicationResponse from(JobApplication app) {
        return from(app, null, null, false);
    }

    public static JobApplicationResponse from(JobApplication app, Double avgRating, Long ratingCount, boolean verified) {
        JobApplicationResponse r = new JobApplicationResponse();
        r.setId(app.getId());
        r.setJobId(app.getJob().getId());
        r.setJobTitle(app.getJob().getTitle());
        r.setJobStatus(app.getJob().getStatus());
        r.setDriverName(app.getDriver().getFirstName() + " " + app.getDriver().getLastName());
        r.setDriverEmail(app.getDriver().getEmail());
        r.setDriverId(app.getDriver().getId());
        r.setStatus(app.getStatus());
        r.setCoverNote(app.getCoverNote());
        r.setAppliedAt(app.getAppliedAt());
        r.setDriverAverageRating(avgRating);
        r.setDriverRatingCount(ratingCount);
        r.setDriverVerified(verified);
        return r;
    }
}
