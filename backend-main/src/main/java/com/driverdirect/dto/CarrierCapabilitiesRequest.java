package com.driverdirect.dto;

import lombok.Data;

import java.util.Set;

/**
 * A carrier's self-declared capabilities (M4): the transport modes it operates
 * and the mode-specific credentials it holds (tagged "MODE:NAME", e.g. "AIR:ATPL").
 */
@Data
public class CarrierCapabilitiesRequest {
    private Set<String> supportedModes; // mode names, e.g. ROAD, RAIL, OCEAN, AIR
    private Set<String> credentials;
}
