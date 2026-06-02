package com.driverdirect.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * One ordered stop on a multi-stop route as submitted by the post-a-load form.
 * Maps onto the {@link com.driverdirect.model.Stop} entity. {@code type} is
 * the {@link com.driverdirect.model.Stop.StopType} enum name; unknown values
 * are coerced to WAYPOINT on the server.
 */
@Data
public class CreateLoadStopRequest {
    private String type;
    private String locationName;
    private String addressLine;
    private String city;
    private String country;
    private Double latitude;
    private Double longitude;
    private LocalDateTime earliestAt;
    private LocalDateTime latestAt;
}
