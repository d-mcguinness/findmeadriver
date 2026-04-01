package com.driverdirect.service;

import com.driverdirect.dto.DriverRegistrationRequest;
import com.driverdirect.model.Driver;

public interface DriverService {

    /**
     * Creates a new driver from the registration request
     *
     * @param registrationRequest the driver registration data
     * @return the created Driver entity
     */
    Driver createDriver(DriverRegistrationRequest registrationRequest);
}
