package com.driverdirect.service;

import com.driverdirect.dto.AvailabilityResponse;
import com.driverdirect.dto.DutyClock;
import com.driverdirect.dto.WeeklyAvailabilityRequest;
import com.driverdirect.model.Carrier;
import com.driverdirect.model.Shipment;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface AvailabilityService {

    AvailabilityResponse setWeeklyAvailability(Carrier carrier, WeeklyAvailabilityRequest request);

    AvailabilityResponse getAvailability(Carrier carrier, LocalDate start, LocalDate end);

    /** Remaining duty hours for one carrier on a date for a given mode:
     *  max(0, declared(mode) − already-committed(mode)). The AVAILABILITY gate. */
    double getRemainingHoursOnDate(Carrier carrier, LocalDate date, Shipment.Mode mode);

    /** Remaining hours per carrier on one date+mode, in a constant number of
     *  queries (batch admin preview). Carriers absent from the map default to 0. */
    Map<Long, Double> getRemainingHoursForCarriers(Collection<Carrier> carriers, LocalDate date, Shipment.Mode mode);

    /** Coarse cross-mode declared hours per date (the browse pre-filter). Dates
     *  with no entry are absent (callers default to 0). */
    Map<LocalDate, Double> getAvailableHoursForDates(Carrier carrier, Collection<LocalDate> dates);

    /** Per-mode duty clocks for the carrier's supported modes, anchored on the
     *  week containing {@code weekStart} (Monday). */
    List<DutyClock> getDutyClocks(Carrier carrier, LocalDate weekStart);
}
