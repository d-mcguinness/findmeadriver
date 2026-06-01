package com.driverdirect.service;

import com.driverdirect.dto.CarrierRegistrationRequest;
import com.driverdirect.model.Carrier;

public interface CarrierService {

    /**
     * Creates a new carrier from the registration request
     *
     * @param registrationRequest the carrier registration data
     * @return the created Carrier entity
     */
    Carrier createCarrier(CarrierRegistrationRequest registrationRequest);
}
