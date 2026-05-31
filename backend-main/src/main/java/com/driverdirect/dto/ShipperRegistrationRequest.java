package com.driverdirect.dto;

import com.driverdirect.model.Shipper;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;

@Data
@EqualsAndHashCode(callSuper = true)
public class ShipperRegistrationRequest extends UserRegistrationRequest {
    @NotBlank
    private String companyName;

    private Shipper.Industry industry;

    private String companyWebsite;
}
