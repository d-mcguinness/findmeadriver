package com.driverdirect.service;

import com.driverdirect.dto.EmployerRegistrationRequest;
import com.driverdirect.model.Employer;

public interface EmployerService {

    /**
     * Creates a new employer from the registration request
     *
     * @param registrationRequest the employer registration data
     * @return the created Employer entity
     */
    Employer createEmployer(EmployerRegistrationRequest registrationRequest);
}
