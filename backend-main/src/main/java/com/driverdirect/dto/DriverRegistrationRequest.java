package com.driverdirect.dto;

import com.driverdirect.model.Driver;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.PositiveOrZero;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
public class DriverRegistrationRequest extends UserRegistrationRequest {
    @NotBlank
    private String licenseNumber;

    @NotNull
    private LocalDate licenseExpiration;

    @NotNull
    private Driver.CDLType cdlType;

    @PositiveOrZero
    private Integer yearsExperience;
    private String licenseState;
    private Integer experienceYears;
}
