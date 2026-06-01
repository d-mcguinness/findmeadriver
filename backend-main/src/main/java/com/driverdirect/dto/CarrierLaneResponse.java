package com.driverdirect.dto;

import com.driverdirect.model.CarrierLane;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CarrierLaneResponse {
    private Long id;
    private String originCountry;
    private String destinationCountry;
    private LocalDateTime createdAt;

    public static CarrierLaneResponse from(CarrierLane l) {
        CarrierLaneResponse r = new CarrierLaneResponse();
        r.setId(l.getId());
        r.setOriginCountry(l.getOriginCountry());
        r.setDestinationCountry(l.getDestinationCountry());
        r.setCreatedAt(l.getCreatedAt());
        return r;
    }
}
