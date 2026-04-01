package com.driverdirect.dto;

import com.driverdirect.model.Employer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;

@Data
@EqualsAndHashCode(callSuper = true)
public class EmployerRegistrationRequest extends UserRegistrationRequest {
    @NotBlank
    private String companyName;

    private Employer.Industry industry;

    private String companyWebsite;
}
