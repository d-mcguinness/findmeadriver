package com.driverdirect.service;

import com.driverdirect.dto.AvailabilityResponse;
import com.driverdirect.dto.WeeklyAvailabilityRequest;
import com.driverdirect.model.Driver;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

public interface AvailabilityService {

    AvailabilityResponse setWeeklyAvailability(Driver driver, WeeklyAvailabilityRequest request);

    AvailabilityResponse getAvailability(Driver driver, LocalDate start, LocalDate end);

    Double getAvailableHoursOnDate(Driver driver, LocalDate date);

    /** Available hours for several dates in one query. Dates with no entry are
     *  absent from the map (callers default to 0). */
    Map<LocalDate, Double> getAvailableHoursForDates(Driver driver, Collection<LocalDate> dates);

    /** Available hours on one date for several drivers in one query, keyed by
     *  driver id. Drivers with no entry are absent (callers default to 0). */
    Map<Long, Double> getAvailableHoursForDrivers(Collection<Driver> drivers, LocalDate date);
}
