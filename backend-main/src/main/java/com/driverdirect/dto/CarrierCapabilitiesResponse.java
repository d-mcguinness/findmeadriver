package com.driverdirect.dto;

import com.driverdirect.model.Carrier;
import lombok.Data;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Data
public class CarrierCapabilitiesResponse {
    private Set<String> supportedModes;
    private Set<String> credentials;

    public static CarrierCapabilitiesResponse from(Carrier c) {
        CarrierCapabilitiesResponse r = new CarrierCapabilitiesResponse();
        r.supportedModes = c.getSupportedModes() == null ? new TreeSet<>()
                : c.getSupportedModes().stream().map(Enum::name).collect(Collectors.toCollection(TreeSet::new));
        r.credentials = c.getCredentials() == null ? new TreeSet<>() : new TreeSet<>(c.getCredentials());
        return r;
    }
}
