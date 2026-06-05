package com.driverdirect.service;

import com.driverdirect.dto.AvailabilityEntry;
import com.driverdirect.dto.AvailabilityResponse;
import com.driverdirect.dto.DutyClock;
import com.driverdirect.dto.WeeklyAvailabilityRequest;
import com.driverdirect.model.Carrier;
import com.driverdirect.model.CarrierAvailability;
import com.driverdirect.model.LoadStatus;
import com.driverdirect.model.Shipment;
import com.driverdirect.repository.CarrierAvailabilityRepository;
import com.driverdirect.repository.LoadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Per-mode duty clocks. A carrier declares availability per transport mode per
 * date; each mode is validated against its OWN duty/rest ceilings (M5 rule-set),
 * and "remaining" hours net off the hours already committed to assigned loads of
 * that mode. So a multi-modal carrier runs separate calendars — e.g. a ROAD clock
 * (EU 561: 56h/wk) and an OCEAN clock (STCW: 91h/wk) — rather than one collapsed pool.
 */
@Service
@RequiredArgsConstructor
public class AvailabilityServiceImpl implements AvailabilityService {

    private final CarrierAvailabilityRepository availabilityRepository;
    private final ComplianceRuleSetRegistry ruleSets;
    private final LoadRepository loadRepository;

    /** Statuses whose load hours count as committed against a mode's clock. */
    private static final List<LoadStatus> COMMITTED_STATUSES =
            List.of(LoadStatus.ASSIGNED, LoadStatus.IN_PROGRESS, LoadStatus.COMPLETED);

    @Override
    @Transactional
    public AvailabilityResponse setWeeklyAvailability(Carrier carrier, WeeklyAvailabilityRequest request) {
        for (AvailabilityEntry entry : request.getEntries()) {
            Shipment.Mode mode = modeOf(entry);
            validateEntry(carrier, entry, request.getEntries(), mode);

            CarrierAvailability availability = availabilityRepository
                    .findByCarrierAndDateAndMode(carrier, entry.getDate(), mode)
                    .orElse(new CarrierAvailability(carrier, entry.getDate(), mode, 0.0));

            availability.setAvailableHours(entry.getAvailableHours());
            availabilityRepository.save(availability);
        }

        LocalDate firstDate = request.getEntries().stream()
                .map(AvailabilityEntry::getDate)
                .min(LocalDate::compareTo)
                .orElse(LocalDate.now());

        LocalDate weekStart = firstDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return getAvailability(carrier, weekStart, weekStart.plusDays(6));
    }

    @Override
    public AvailabilityResponse getAvailability(Carrier carrier, LocalDate start, LocalDate end) {
        List<CarrierAvailability> entries = availabilityRepository.findByCarrierAndDateBetween(carrier, start, end);

        AvailabilityResponse response = new AvailabilityResponse();
        response.setDays(entries.stream()
                .map(e -> new AvailabilityResponse.DayAvailability(e.getId(), e.getDate(), e.getMode(), e.getAvailableHours()))
                .collect(Collectors.toList()));

        LocalDate weekStart = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);
        double weeklyTotal = sumAllModes(carrier, weekStart, weekEnd);
        double prevWeekTotal = sumAllModes(carrier, weekStart.minusWeeks(1), weekStart.minusDays(1));

        response.setWeeklyTotal(round(weeklyTotal));
        response.setFortnightlyTotal(round(weeklyTotal + prevWeekTotal));
        // Per-mode headroom now lives in dutyClocks; the single aggregate remainings
        // are no longer meaningful across modes.
        response.setWeeklyRemaining(null);
        response.setFortnightlyRemaining(null);
        response.setDutyClocks(getDutyClocks(carrier, weekStart));

        return response;
    }

    @Override
    public double getRemainingHoursOnDate(Carrier carrier, LocalDate date, Shipment.Mode mode) {
        Shipment.Mode m = mode != null ? mode : Shipment.Mode.ROAD;
        double declared = availabilityRepository.findByCarrierAndDateAndMode(carrier, date, m)
                .map(CarrierAvailability::getAvailableHours).orElse(0.0);
        double committed = loadRepository.sumCommittedHours(carrier, COMMITTED_STATUSES, m, date);
        return Math.max(0, declared - committed);
    }

    @Override
    public Map<Long, Double> getRemainingHoursForCarriers(Collection<Carrier> carriers, LocalDate date, Shipment.Mode mode) {
        if (carriers.isEmpty() || date == null) return Map.of();
        Shipment.Mode m = mode != null ? mode : Shipment.Mode.ROAD;

        Map<Long, Double> declared = new HashMap<>();
        for (CarrierAvailability a : availabilityRepository.findByDateAndModeAndCarrierIn(date, m, carriers)) {
            declared.put(a.getCarrier().getId(), a.getAvailableHours());
        }
        Map<Long, Double> committed = new HashMap<>();
        for (Object[] row : loadRepository.sumCommittedHoursByCarrier(carriers, COMMITTED_STATUSES, m, date)) {
            committed.put((Long) row[0], ((Number) row[1]).doubleValue());
        }
        Map<Long, Double> out = new HashMap<>();
        for (Map.Entry<Long, Double> e : declared.entrySet()) {
            out.put(e.getKey(), Math.max(0, e.getValue() - committed.getOrDefault(e.getKey(), 0.0)));
        }
        return out;
    }

    @Override
    public Map<Shipment.Mode, Map<LocalDate, Double>> getRemainingHoursByModeAndDate(
            Carrier carrier, Collection<LocalDate> dates) {
        if (dates.isEmpty()) return Map.of();
        // declared per (mode, date)
        Map<Shipment.Mode, Map<LocalDate, Double>> out = new HashMap<>();
        for (CarrierAvailability a : availabilityRepository.findByCarrierAndDateIn(carrier, dates)) {
            out.computeIfAbsent(a.getMode(), k -> new HashMap<>())
               .merge(a.getDate(), a.getAvailableHours(), Double::sum);
        }
        // net off committed hours per (date, mode)
        for (Object[] row : loadRepository.sumCommittedHoursByDateAndMode(carrier, COMMITTED_STATUSES, dates)) {
            if (row[0] == null || row[1] == null) continue;
            LocalDate date = (LocalDate) row[0];
            Shipment.Mode mode = (Shipment.Mode) row[1];
            double committed = ((Number) row[2]).doubleValue();
            Map<LocalDate, Double> byDate = out.get(mode);
            if (byDate != null && byDate.containsKey(date)) {
                byDate.put(date, Math.max(0, byDate.get(date) - committed));
            }
        }
        return out;
    }

    @Override
    public List<DutyClock> getDutyClocks(Carrier carrier, LocalDate weekStart) {
        LocalDate weekEnd = weekStart.plusDays(6);
        LocalDate fortStart = weekStart.minusWeeks(1);

        List<CarrierAvailability> declared = availabilityRepository.findByCarrierAndDateBetween(carrier, fortStart, weekEnd);
        Map<Shipment.Mode, Double> committedWeek = committedByMode(carrier, weekStart, weekEnd);
        Map<Shipment.Mode, Double> committedFort = committedByMode(carrier, fortStart, weekEnd);

        List<DutyClock> clocks = new ArrayList<>();
        for (Shipment.Mode mode : clockModes(carrier)) {
            double declaredWeek = declared.stream()
                    .filter(a -> a.getMode() == mode && !a.getDate().isBefore(weekStart) && !a.getDate().isAfter(weekEnd))
                    .mapToDouble(CarrierAvailability::getAvailableHours).sum();
            double declaredFort = declared.stream()
                    .filter(a -> a.getMode() == mode)
                    .mapToDouble(CarrierAvailability::getAvailableHours).sum();
            double cWeek = committedWeek.getOrDefault(mode, 0.0);
            double cFort = committedFort.getOrDefault(mode, 0.0);

            ComplianceRuleSet rs = ruleSets.forMode(mode);
            double remWeek = Math.max(0, Math.min(rs.maxWeeklyHours(), declaredWeek) - cWeek);
            double remFort = Math.max(0, Math.min(rs.maxFortnightlyHours(), declaredFort) - cFort);

            clocks.add(new DutyClock(mode.name(), rs.regulation(),
                    rs.maxDailyHours(), rs.maxWeeklyHours(), rs.maxFortnightlyHours(),
                    round(declaredWeek), round(cWeek), round(remWeek),
                    round(declaredFort), round(cFort), round(remFort)));
        }
        return clocks;
    }

    // ---- helpers ----

    private Shipment.Mode modeOf(AvailabilityEntry entry) {
        return entry.getMode() != null ? entry.getMode() : Shipment.Mode.ROAD;
    }

    /** Modes a carrier gets a clock for: their supported modes that have a rule-set
     *  (ROAD/RAIL/OCEAN/AIR), in enum order; road-only when none. */
    private List<Shipment.Mode> clockModes(Carrier carrier) {
        Set<Shipment.Mode> ruled = ruleSets.all().keySet();
        Set<Shipment.Mode> supported = carrier == null ? null : carrier.getSupportedModes();
        List<Shipment.Mode> out = new ArrayList<>();
        if (supported == null || supported.isEmpty()) {
            out.add(Shipment.Mode.ROAD);
            return out;
        }
        for (Shipment.Mode m : Shipment.Mode.values()) {       // stable enum order
            if (supported.contains(m) && ruled.contains(m)) out.add(m);
        }
        if (out.isEmpty()) out.add(Shipment.Mode.ROAD);
        return out;
    }

    private Map<Shipment.Mode, Double> committedByMode(Carrier carrier, LocalDate start, LocalDate end) {
        Map<Shipment.Mode, Double> m = new HashMap<>();
        for (Object[] row : loadRepository.sumCommittedHoursByMode(carrier, COMMITTED_STATUSES, start, end)) {
            if (row[0] != null) m.put((Shipment.Mode) row[0], ((Number) row[1]).doubleValue());
        }
        return m;
    }

    private double sumAllModes(Carrier carrier, LocalDate start, LocalDate end) {
        return availabilityRepository.findByCarrierAndDateBetween(carrier, start, end).stream()
                .mapToDouble(CarrierAvailability::getAvailableHours).sum();
    }

    private double sumMode(Carrier carrier, LocalDate start, LocalDate end, Shipment.Mode mode) {
        return availabilityRepository.findByCarrierAndDateBetweenAndMode(carrier, start, end, mode).stream()
                .mapToDouble(CarrierAvailability::getAvailableHours).sum();
    }

    private double sumModeExcluding(Carrier carrier, LocalDate start, LocalDate end, Shipment.Mode mode, LocalDate exclude) {
        return availabilityRepository.findByCarrierAndDateBetweenAndMode(carrier, start, end, mode).stream()
                .filter(e -> !e.getDate().equals(exclude))
                .mapToDouble(CarrierAvailability::getAvailableHours).sum();
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private void validateEntry(Carrier carrier, AvailabilityEntry entry, List<AvailabilityEntry> allEntries, Shipment.Mode mode) {
        // The ceiling is the LOAD's mode rule-set (M5): road = EU 561, air = FTL, sea = STCW, rail = EU rail.
        ComplianceRuleSet rs = ruleSets.forMode(mode);
        if (entry.getAvailableHours() < 0) {
            throw new IllegalArgumentException("Available hours cannot be negative");
        }
        if (entry.getAvailableHours() > rs.maxDailyHours()) {
            throw new IllegalArgumentException(
                    "Maximum " + rs.maxDailyHours() + " hours per day for " + mode + " (" + rs.regulation() + ")");
        }

        LocalDate weekStart = entry.getDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);

        // Extended-day limit: only modes where defaultDailyMax < maxDailyHours. Counted within this mode.
        if (entry.getAvailableHours() > rs.defaultDailyMax()) {
            long dbExtended = availabilityRepository.findByCarrierAndDateBetweenAndMode(carrier, weekStart, weekEnd, mode).stream()
                    .filter(e -> !e.getDate().equals(entry.getDate()))
                    .filter(e -> e.getAvailableHours() > rs.defaultDailyMax())
                    .count();
            long batchExtended = allEntries.stream()
                    .filter(e -> modeOf(e) == mode)
                    .filter(e -> !e.getDate().equals(entry.getDate()))
                    .filter(e -> inWeek(e.getDate(), weekStart, weekEnd))
                    .filter(e -> e.getAvailableHours() > rs.defaultDailyMax())
                    .count();
            if (Math.max(dbExtended, batchExtended) >= rs.maxExtendedDaysPerWeek()) {
                throw new IllegalArgumentException(
                        "Maximum " + rs.maxExtendedDaysPerWeek() + " days per week can exceed " +
                        rs.defaultDailyMax() + " hours for " + mode + " (" + rs.regulation() + ")");
            }
        }

        // Weekly ceiling (this mode only).
        double currentWeekTotal = sumModeExcluding(carrier, weekStart, weekEnd, mode, entry.getDate());
        double batchTotal = allEntries.stream()
                .filter(e -> modeOf(e) == mode)
                .filter(e -> !e.getDate().equals(entry.getDate()))
                .filter(e -> inWeek(e.getDate(), weekStart, weekEnd))
                .mapToDouble(AvailabilityEntry::getAvailableHours)
                .sum();
        double projectedWeekly = Math.max(currentWeekTotal, batchTotal) + entry.getAvailableHours();
        if (projectedWeekly > rs.maxWeeklyHours()) {
            throw new IllegalArgumentException(
                    "Weekly " + mode + " total would exceed " + rs.maxWeeklyHours() + " hours (" + rs.regulation() + ")");
        }

        // Fortnightly ceiling (this mode only).
        double prevWeekTotal = sumMode(carrier, weekStart.minusWeeks(1), weekStart.minusDays(1), mode);
        if (projectedWeekly + prevWeekTotal > rs.maxFortnightlyHours()) {
            throw new IllegalArgumentException(
                    "Fortnightly " + mode + " total would exceed " + rs.maxFortnightlyHours() + " hours (" + rs.regulation() + ")");
        }
    }

    private static boolean inWeek(LocalDate d, LocalDate weekStart, LocalDate weekEnd) {
        return !d.isBefore(weekStart) && !d.isAfter(weekEnd);
    }
}
