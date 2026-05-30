package com.driverdirect.dto;

import com.driverdirect.model.Location;
import lombok.Data;

@Data
public class LocationResponse {
    private Long id;
    private Long ownerEmployerId;
    private String name;
    private String addressLine;
    private String city;
    private String country;
    private String locationType;
    private String unlocode;
    private String iata;
    private Double latitude;
    private Double longitude;
    private String timezone;
    private String operatingHours;

    public static LocationResponse from(Location l) {
        LocationResponse r = new LocationResponse();
        r.setId(l.getId());
        r.setOwnerEmployerId(l.getOwnerEmployer() != null ? l.getOwnerEmployer().getId() : null);
        r.setName(l.getName());
        r.setAddressLine(l.getAddressLine());
        r.setCity(l.getCity());
        r.setCountry(l.getCountry());
        r.setLocationType(l.getLocationType() != null ? l.getLocationType().name() : null);
        r.setUnlocode(l.getUnlocode());
        r.setIata(l.getIata());
        r.setLatitude(l.getLatitude());
        r.setLongitude(l.getLongitude());
        r.setTimezone(l.getTimezone());
        r.setOperatingHours(l.getOperatingHours());
        return r;
    }
}
