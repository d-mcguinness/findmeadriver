package com.driverdirect.service;

import com.driverdirect.dto.AvailabilityEntry;
import com.driverdirect.dto.AvailabilityResponse;
import com.driverdirect.dto.WeeklyAvailabilityRequest;
import com.driverdirect.model.Driver;
import com.driverdirect.model.DriverAvailability;
import com.driverdirect.repository.DriverAvailabilityRepository;
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

    private final DriverAvailabilityRepository availabilityRepository;

    @Override
    @Transactional
    public AvailabilityResponse setWeeklyAvailability(Driver driver, WeeklyAvailabilityRequest request) {
        for (AvailabilityEntry entry : request.getEntries()) {
            validateEntry(driver, entry, request.getEntries());

            DriverAvailability availability = availabilityRepository
                    .findByDriverAndDate(driver, entry.getDate())
                    .orElse(new DriverAvailability(driver, entry.getDate(), 0.0));

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

        return getAvailability(driver, weekStart, weekEnd);
    }

    @Override
    public AvailabilityResponse getAvailability(Driver driver, LocalDate start, LocalDate end) {
        List<DriverAvailability> entries = availabilityRepository.findByDriverAndDateBetween(driver, start, end);

        AvailabilityResponse response = new AvailabilityResponse();
        response.setDays(entries.stream()
                .map(e -> new AvailabilityResponse.DayAvailability(e.getId(), e.getDate(), e.getAvailableHours()))
                .collect(Collectors.toList()));

        // Calculate weekly total (Mon-Sun of the start date's week)
        LocalDate weekStart = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);
        double weeklyTotal = calculateTotalHours(driver, weekStart, weekEnd);

        // Calculate fortnightly total (this week + previous week)
        LocalDate prevWeekStart = weekStart.minusWeeks(1);
        double prevWeekTotal = calculateTotalHours(driver, prevWeekStart, weekStart.minusDays(1));
        double fortnightlyTotal = weeklyTotal + prevWeekTotal;

        response.setWeeklyTotal(weeklyTotal);
        response.setFortnightlyTotal(fortnightlyTotal);
        response.setWeeklyRemaining(MAX_WEEKLY_HOURS - weeklyTotal);
        response.setFortnightlyRemaining(MAX_FORTNIGHTLY_HOURS - fortnightlyTotal);

        return response;
    }

    @Override
    public Double getAvailableHoursOnDate(Driver driver, LocalDate date) {
        return availabilityRepository.findByDriverAndDate(driver, date)
                .map(DriverAvailability::getAvailableHours)
                .orElse(0.0);
    }

    @Override
    public Map<LocalDate, Double> getAvailableHoursForDates(Driver driver, Collection<LocalDate> dates) {
        if (dates.isEmpty()) return Map.of();
        Map<LocalDate, Double> byDate = new HashMap<>();
        for (DriverAvailability a : availabilityRepository.findByDriverAndDateIn(driver, dates)) {
            byDate.put(a.getDate(), a.getAvailableHours());
        }
        return byDate;
    }

    @Override
    public Map<Long, Double> getAvailableHoursForDrivers(Collection<Driver> drivers, LocalDate date) {
        if (drivers.isEmpty() || date == null) return Map.of();
        Map<Long, Double> byDriver = new HashMap<>();
        for (DriverAvailability a : availabilityRepository.findByDateAndDriverIn(date, drivers)) {
            byDriver.put(a.getDriver().getId(), a.getAvailableHours());
        }
        return byDriver;
    }

    private void validateEntry(Driver driver, AvailabilityEntry entry, List<AvailabilityEntry> allEntries) {
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
            List<DriverAvailability> weekEntries = availabilityRepository
                    .findByDriverAndDateBetween(driver, weekStart, weekEnd);
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
        double currentWeekTotal = calculateTotalHoursExcluding(driver, weekStart, weekEnd, entry.getDate());

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
        double prevWeekTotal = calculateTotalHours(driver, prevWeekStart, weekStart.minusDays(1));
        if (projectedWeekly + prevWeekTotal > MAX_FORTNIGHTLY_HOURS) {
            throw new IllegalArgumentException(
                    "Fortnightly total would exceed " + MAX_FORTNIGHTLY_HOURS + " hours (EU tachograph regulation). " +
                    "Previous week: " + prevWeekTotal + "h, projected this week: " + projectedWeekly + "h");
        }
    }

    private double calculateTotalHours(Driver driver, LocalDate start, LocalDate end) {
        return availabilityRepository.findByDriverAndDateBetween(driver, start, end).stream()
                .mapToDouble(DriverAvailability::getAvailableHours)
                .sum();
    }

    private double calculateTotalHoursExcluding(Driver driver, LocalDate start, LocalDate end, LocalDate exclude) {
        return availabilityRepository.findByDriverAndDateBetween(driver, start, end).stream()
                .filter(e -> !e.getDate().equals(exclude))
                .mapToDouble(DriverAvailability::getAvailableHours)
                .sum();
    }
}
