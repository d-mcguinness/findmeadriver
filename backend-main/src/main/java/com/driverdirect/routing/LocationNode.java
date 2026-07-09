package com.driverdirect.routing;

import com.driverdirect.model.Location;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * An immutable snapshot of one {@link Location}, detached from JPA. The graph
 * must stay usable after the building transaction closes, so edges and the
 * search only ever see these — never live entities.
 *
 * <p>The zone is resolved here, at build time ("timezone resolution is the
 * graph build's job" — CarrierLane.nextDeparture javadoc): {@code
 * Location.timezone} when it parses, UTC otherwise. UTC is a documented
 * fallback, not a guess — seeded and ad-hoc locations don't declare
 * timezones yet.
 */
public record LocationNode(
        Long id,
        String name,
        Location.LocationType type,
        String country,
        Double latitude,
        Double longitude,
        ZoneId zone) {

    public static LocationNode from(Location location) {
        return new LocationNode(location.getId(), location.getName(),
                location.getLocationType(), location.getCountry(),
                location.getLatitude(), location.getLongitude(),
                zoneOf(location.getTimezone()));
    }

    private static ZoneId zoneOf(String timezone) {
        if (timezone == null || timezone.isBlank()) return ZoneOffset.UTC;
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException e) {
            return ZoneOffset.UTC; // fail soft, like CarrierLane.getDepartureDaySet
        }
    }

    public boolean hasCoordinates() {
        return latitude != null && longitude != null;
    }

    /** Great-circle (haversine) distance in km; callers must check
     *  {@link #hasCoordinates()} on both ends first. */
    public double greatCircleKm(LocationNode other) {
        double lat1 = Math.toRadians(latitude);
        double lat2 = Math.toRadians(other.latitude);
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(other.longitude - longitude);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
