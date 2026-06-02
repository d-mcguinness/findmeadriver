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

    private final CarrierAvailabilityRepository availabilityRepository;
    private final ComplianceRuleSetRegistry ruleSets;

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

        ComplianceRuleSet rs = ruleSets.forCarrier(carrier);
        response.setWeeklyTotal(weeklyTotal);
        response.setFortnightlyTotal(fortnightlyTotal);
        response.setWeeklyRemaining(rs.maxWeeklyHours() - weeklyTotal);
        response.setFortnightlyRemaining(rs.maxFortnightlyHours() - fortnightlyTotal);

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
        // Mode-specific duty/rest ceilings (M5): road = EU 561, air = FTL, sea = STCW, …
        ComplianceRuleSet rs = ruleSets.forCarrier(carrier);
        if (entry.getAvailableHours() < 0) {
            throw new IllegalArgumentException("Available hours cannot be negative");
        }
        if (entry.getAvailableHours() > rs.maxDailyHours()) {
            throw new IllegalArgumentException(
                    "Maximum " + rs.maxDailyHours() + " hours per day (" + rs.regulation() + ")");
        }

        // Extended-day limit: only modes where defaultDailyMax < maxDailyHours.
        if (entry.getAvailableHours() > rs.defaultDailyMax()) {
            LocalDate weekStart = entry.getDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate weekEnd = weekStart.plusDays(6);

            // Count extended days from existing DB records
            List<CarrierAvailability> weekEntries = availabilityRepository
                    .findByCarrierAndDateBetween(carrier, weekStart, weekEnd);
            long extendedDays = weekEntries.stream()
                    .filter(e -> !e.getDate().equals(entry.getDate()))
                    .filter(e -> e.getAvailableHours() > rs.defaultDailyMax())
                    .count();

            // Also count from the current batch being submitted
            long batchExtendedDays = allEntries.stream()
                    .filter(e -> !e.getDate().equals(entry.getDate()))
                    .filter(e -> e.getDate().compareTo(weekStart) >= 0 && e.getDate().compareTo(weekEnd) <= 0)
                    .filter(e -> e.getAvailableHours() > rs.defaultDailyMax())
                    .count();

            // Use the higher of the two counts (batch entries will replace DB entries)
            long totalExtended = Math.max(extendedDays, batchExtendedDays);
            if (totalExtended >= rs.maxExtendedDaysPerWeek()) {
                throw new IllegalArgumentException(
                        "Maximum " + rs.maxExtendedDaysPerWeek() + " days per week can exceed " +
                        rs.defaultDailyMax() + " hours (" + rs.regulation() + ")");
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
        if (projectedWeekly > rs.maxWeeklyHours()) {
            throw new IllegalArgumentException(
                    "Weekly total would exceed " + rs.maxWeeklyHours() + " hours (" + rs.regulation() + "). " +
                    "Current week: " + currentWeekTotal + "h, this entry: " + entry.getAvailableHours() + "h");
        }

        // Check fortnightly total
        LocalDate prevWeekStart = weekStart.minusWeeks(1);
        double prevWeekTotal = calculateTotalHours(carrier, prevWeekStart, weekStart.minusDays(1));
        if (projectedWeekly + prevWeekTotal > rs.maxFortnightlyHours()) {
            throw new IllegalArgumentException(
                    "Fortnightly total would exceed " + rs.maxFortnightlyHours() + " hours (" + rs.regulation() + "). " +
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
