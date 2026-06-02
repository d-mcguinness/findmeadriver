package com.driverdirect.service;

import com.driverdirect.dto.AvailabilityResponse;
import com.driverdirect.dto.WeeklyAvailabilityRequest;
import com.driverdirect.model.Carrier;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

public interface AvailabilityService {

    AvailabilityResponse setWeeklyAvailability(Carrier carrier, WeeklyAvailabilityRequest request);

    AvailabilityResponse getAvailability(Carrier carrier, LocalDate start, LocalDate end);

    Double getAvailableHoursOnDate(Carrier carrier, LocalDate date);

    /** Available hours for several dates in one query. Dates with no entry are
     *  absent from the map (callers default to 0). */
    Map<LocalDate, Double> getAvailableHoursForDates(Carrier carrier, Collection<LocalDate> dates);

    /** Available hours on one date for several carriers in one query, keyed by
     *  carrier id. Carriers with no entry are absent (callers default to 0). */
    Map<Long, Double> getAvailableHoursForCarriers(Collection<Carrier> carriers, LocalDate date);
}
