package com.driverdirect.service;

import com.driverdirect.dto.AvailabilityResponse;
import com.driverdirect.dto.WeeklyAvailabilityRequest;
import com.driverdirect.model.Driver;

import java.time.LocalDate;

public interface AvailabilityService {

    AvailabilityResponse setWeeklyAvailability(Driver driver, WeeklyAvailabilityRequest request);

    AvailabilityResponse getAvailability(Driver driver, LocalDate start, LocalDate end);

    Double getAvailableHoursOnDate(Driver driver, LocalDate date);
}
