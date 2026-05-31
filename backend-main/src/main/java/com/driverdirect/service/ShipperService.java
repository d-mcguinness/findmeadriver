package com.driverdirect.service;

import com.driverdirect.dto.ShipperRegistrationRequest;
import com.driverdirect.model.Shipper;

public interface ShipperService {

    /**
     * Creates a new shipper from the registration request
     *
     * @param registrationRequest the shipper registration data
     * @return the created Shipper entity
     */
    Shipper createShipper(ShipperRegistrationRequest registrationRequest);
}
