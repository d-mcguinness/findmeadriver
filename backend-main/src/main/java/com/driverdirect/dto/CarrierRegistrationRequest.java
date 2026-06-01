package com.driverdirect.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.PositiveOrZero;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
public class CarrierRegistrationRequest extends UserRegistrationRequest {
    @NotBlank
    private String licenseNumber;

    @NotNull
    private LocalDate licenseExpiration;

    // Free-form licence category. UI picks values from a country-aware lookup
    // (e.g. "CLASS_A" for US, "C" / "C+E" for EU).
    @NotBlank
    private String licenceCategory;

    @PositiveOrZero
    private Integer yearsExperience;
    private String licenseState;
    private Integer experienceYears;
}
