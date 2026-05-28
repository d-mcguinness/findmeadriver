package com.driverdirect.dto;

import com.driverdirect.model.DriverLane;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DriverLaneResponse {
    private Long id;
    private String originCountry;
    private String destinationCountry;
    private LocalDateTime createdAt;

    public static DriverLaneResponse from(DriverLane l) {
        DriverLaneResponse r = new DriverLaneResponse();
        r.setId(l.getId());
        r.setOriginCountry(l.getOriginCountry());
        r.setDestinationCountry(l.getDestinationCountry());
        r.setCreatedAt(l.getCreatedAt());
        return r;
    }
}
