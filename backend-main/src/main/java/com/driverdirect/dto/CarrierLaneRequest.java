package com.driverdirect.dto;

import lombok.Data;

@Data
public class CarrierLaneRequest {
    private String originCountry;
    private String destinationCountry;
}
