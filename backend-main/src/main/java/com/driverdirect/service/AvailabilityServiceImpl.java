package com.driverdirect.service;

import com.driverdirect.dto.AvailabilityEntry;
import com.driverdirect.dto.AvailabilityResponse;
import com.driverdirect.dto.WeeklyAvailabilityRequest;
import com.driverdirect.model.Carrier;
import com.driverdirect.model.CarrierAvailability;
import com.driverdirect.repository.CarrierAvailabilityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AvailabilityServiceImpl implements AvailabilityService {

    private static final double MAX_DAILY_HOURS = 10.0;
    private static final double DEFAULT_DAILY_MAX = 9.0;
    private static final int MAX_EXTENDED_DAYS_PER_WEEK = 2;
    private static final double MAX_WEEKLY_HOURS = 56.0;
    private static final double MAX_FORTNIGHTLY_HOURS = 90.0;

    private final CarrierAvailabilityRepository availabilityRepository;

    @Override
    @Transactional
    public AvailabilityResponse setWeeklyAvailability(Carrier carrier, WeeklyAvailabilityRequest request) {
        for (AvailabilityEntry entry : request.getEntries()) {
            validateEntry(carrier, entry, request.getEntries());

            CarrierAvailability availability = availabilityRepository
                    .findByCarrierAndDate(carrier, entry.getDate())
                    .orElse(new CarrierAvailability(carrier, entry.getDate(), 0.0));

            availability.setAvailableHours(entry.getAvailableHours());
            availabilityRepository.save(availability);
        }

        // Return the current week's availability
        LocalDate firstDate = request.getEntries().stream()
                .map(AvailabilityEntry::getDate)
                .min(LocalDate::compareTo)
                .orElse(LocalDate.now());

        LocalDate weekStart = firstDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);

        return getAvailability(carrier, weekStart, weekEnd);
    }

    @Override
    public AvailabilityResponse getAvailability(Carrier carrier, LocalDate start, LocalDate end) {
        List<CarrierAvailability> entries = availabilityRepository.findByCarrierAndDateBetween(carrier, start, end);

        AvailabilityResponse response = new AvailabilityResponse();
        response.setDays(entries.stream()
                .map(e -> new AvailabilityResponse.DayAvailability(e.getId(), e.getDate(), e.getAvailableHours()))
                .collect(Collectors.toList()));

        // Calculate weekly total (Mon-Sun of the start date's week)
        LocalDate weekStart = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);
        double weeklyTotal = calculateTotalHours(carrier, weekStart, weekEnd);

        // Calculate fortnightly total (this week + previous week)
        LocalDate prevWeekStart = weekStart.minusWeeks(1);
        double prevWeekTotal = calculateTotalHours(carrier, prevWeekStart, weekStart.minusDays(1));
        double fortnightlyTotal = weeklyTotal + prevWeekTotal;

        response.setWeeklyTotal(weeklyTotal);
        response.setFortnightlyTotal(fortnightlyTotal);
        response.setWeeklyRemaining(MAX_WEEKLY_HOURS - weeklyTotal);
        response.setFortnightlyRemaining(MAX_FORTNIGHTLY_HOURS - fortnightlyTotal);

        return response;
    }

    @Override
    public Double getAvailableHoursOnDate(Carrier carrier, LocalDate date) {
        return availabilityRepository.findByCarrierAndDate(carrier, date)
                .map(CarrierAvailability::getAvailableHours)
                .orElse(0.0);
    }

    @Override
    public Map<LocalDate, Double> getAvailableHoursForDates(Carrier carrier, Collection<LocalDate> dates) {
        if (dates.isEmpty()) return Map.of();
        Map<LocalDate, Double> byDate = new HashMap<>();
        for (CarrierAvailability a : availabilityRepository.findByCarrierAndDateIn(carrier, dates)) {
            byDate.put(a.getDate(), a.getAvailableHours());
        }
        return byDate;
    }

    @Override
    public Map<Long, Double> getAvailableHoursForCarriers(Collection<Carrier> carriers, LocalDate date) {
        if (carriers.isEmpty() || date == null) return Map.of();
        Map<Long, Double> byCarrier = new HashMap<>();
        for (CarrierAvailability a : availabilityRepository.findByDateAndCarrierIn(date, carriers)) {
            byCarrier.put(a.getCarrier().getId(), a.getAvailableHours());
        }
        return byCarrier;
    }

    private void validateEntry(Carrier carrier, AvailabilityEntry entry, List<AvailabilityEntry> allEntries) {
        if (entry.getAvailableHours() < 0) {
            throw new IllegalArgumentException("Available hours cannot be negative");
        }
        if (entry.getAvailableHours() > MAX_DAILY_HOURS) {
            throw new IllegalArgumentException(
                    "Maximum " + MAX_DAILY_HOURS + " hours per day (EU tachograph regulation)");
        }

        // Check extended day limit: max 2 days per week over 9 hours
        if (entry.getAvailableHours() > DEFAULT_DAILY_MAX) {
            LocalDate weekStart = entry.getDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate weekEnd = weekStart.plusDays(6);

            // Count extended days from existing DB records
            List<CarrierAvailability> weekEntries = availabilityRepository
                    .findByCarrierAndDateBetween(carrier, weekStart, weekEnd);
            long extendedDays = weekEntries.stream()
                    .filter(e -> !e.getDate().equals(entry.getDate()))
                    .filter(e -> e.getAvailableHours() > DEFAULT_DAILY_MAX)
                    .count();

            // Also count from the current batch being submitted
            long batchExtendedDays = allEntries.stream()
                    .filter(e -> !e.getDate().equals(entry.getDate()))
                    .filter(e -> e.getDate().compareTo(weekStart) >= 0 && e.getDate().compareTo(weekEnd) <= 0)
                    .filter(e -> e.getAvailableHours() > DEFAULT_DAILY_MAX)
                    .count();

            // Use the higher of the two counts (batch entries will replace DB entries)
            long totalExtended = Math.max(extendedDays, batchExtendedDays);
            if (totalExtended >= MAX_EXTENDED_DAYS_PER_WEEK) {
                throw new IllegalArgumentException(
                        "Maximum " + MAX_EXTENDED_DAYS_PER_WEEK + " days per week can exceed " +
                        DEFAULT_DAILY_MAX + " hours (EU tachograph regulation)");
            }
        }

        // Check weekly total
        LocalDate weekStart = entry.getDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);
        double currentWeekTotal = calculateTotalHoursExcluding(carrier, weekStart, weekEnd, entry.getDate());

        // Add hours from batch entries for the same week (excluding current entry)
        double batchTotal = allEntries.stream()
                .filter(e -> !e.getDate().equals(entry.getDate()))
                .filter(e -> e.getDate().compareTo(weekStart) >= 0 && e.getDate().compareTo(weekEnd) <= 0)
                .mapToDouble(AvailabilityEntry::getAvailableHours)
                .sum();

        double projectedWeekly = Math.max(currentWeekTotal, batchTotal) + entry.getAvailableHours();
        if (projectedWeekly > MAX_WEEKLY_HOURS) {
            throw new IllegalArgumentException(
                    "Weekly total would exceed " + MAX_WEEKLY_HOURS + " hours (EU tachograph regulation). " +
                    "Current week: " + currentWeekTotal + "h, this entry: " + entry.getAvailableHours() + "h");
        }

        // Check fortnightly total
        LocalDate prevWeekStart = weekStart.minusWeeks(1);
        double prevWeekTotal = calculateTotalHours(carrier, prevWeekStart, weekStart.minusDays(1));
        if (projectedWeekly + prevWeekTotal > MAX_FORTNIGHTLY_HOURS) {
            throw new IllegalArgumentException(
                    "Fortnightly total would exceed " + MAX_FORTNIGHTLY_HOURS + " hours (EU tachograph regulation). " +
                    "Previous week: " + prevWeekTotal + "h, projected this week: " + projectedWeekly + "h");
        }
    }

    private double calculateTotalHours(Carrier carrier, LocalDate start, LocalDate end) {
        return availabilityRepository.findByCarrierAndDateBetween(carrier, start, end).stream()
                .mapToDouble(CarrierAvailability::getAvailableHours)
                .sum();
    }

    private double calculateTotalHoursExcluding(Carrier carrier, LocalDate start, LocalDate end, LocalDate exclude) {
        return availabilityRepository.findByCarrierAndDateBetween(carrier, start, end).stream()
                .filter(e -> !e.getDate().equals(exclude))
                .mapToDouble(CarrierAvailability::getAvailableHours)
                .sum();
    }
}
