package com.driverdirect.service;

import com.driverdirect.dto.AvailabilityEntry;
import com.driverdirect.dto.DutyClock;
import com.driverdirect.dto.WeeklyAvailabilityRequest;
import com.driverdirect.model.Carrier;
import com.driverdirect.model.CarrierAvailability;
import com.driverdirect.model.Shipment;
import com.driverdirect.repository.CarrierAvailabilityRepository;
import com.driverdirect.repository.LoadRepository;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Per-mode duty clocks: each mode is validated against its OWN ceilings (not the
 * collapsed ROAD baseline), and remaining hours net off the hours already committed
 * to that mode's assigned loads. Repositories are mocked; the rule-set is real.
 */
class DutyClockServiceTest {

    private final CarrierAvailabilityRepository availRepo = mock(CarrierAvailabilityRepository.class);
    private final LoadRepository loadRepo = mock(LoadRepository.class);
    private final ComplianceRuleSetRegistry rules = new ComplianceRuleSetRegistry();
    private final AvailabilityServiceImpl service = new AvailabilityServiceImpl(availRepo, rules, loadRepo);

    private Carrier carrier(Shipment.Mode... modes) {
        Carrier c = new Carrier();
        c.setId(1L);
        c.setSupportedModes(new HashSet<>(Set.of(modes)));
        return c;
    }

    private AvailabilityEntry entry(LocalDate date, Shipment.Mode mode, double hours) {
        AvailabilityEntry e = new AvailabilityEntry();
        e.setDate(date);
        e.setMode(mode);
        e.setAvailableHours(hours);
        return e;
    }

    private WeeklyAvailabilityRequest request(AvailabilityEntry... entries) {
        WeeklyAvailabilityRequest r = new WeeklyAvailabilityRequest();
        r.setEntries(List.of(entries));
        return r;
    }

    // ---- per-mode daily ceilings: the headline (not collapsed to ROAD) ----

    @Test
    void dailyCeilingIsPerMode() {
        when(availRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        Carrier c = carrier(Shipment.Mode.ROAD, Shipment.Mode.OCEAN);
        LocalDate d = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        // 13h on the OCEAN clock is fine (STCW allows up to 14h/day)...
        assertThatCode(() -> service.setWeeklyAvailability(c, request(entry(d, Shipment.Mode.OCEAN, 13.0))))
                .doesNotThrowAnyException();
        // ...but 13h on the ROAD clock is rejected (EU 561 caps at 10h/day) —
        // even though this is the same multi-modal carrier.
        assertThatThrownBy(() -> service.setWeeklyAvailability(c, request(entry(d, Shipment.Mode.ROAD, 13.0))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10");
    }

    @Test
    void nullModeEntryDefaultsToRoadCeiling() {
        Carrier c = carrier(Shipment.Mode.ROAD, Shipment.Mode.OCEAN);
        LocalDate d = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        // mode == null → treated as ROAD → 13h exceeds the road 10h/day ceiling.
        assertThatThrownBy(() -> service.setWeeklyAvailability(c, request(entry(d, null, 13.0))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- remaining = declared − committed (consumption draw-down) ----

    @Test
    void remainingNetsOffCommittedLoadsForThatMode() {
        Carrier c = carrier(Shipment.Mode.OCEAN);
        LocalDate d = LocalDate.now();
        when(availRepo.findByCarrierAndDateAndMode(c, d, Shipment.Mode.OCEAN))
                .thenReturn(Optional.of(new CarrierAvailability(c, d, Shipment.Mode.OCEAN, 12.0)));
        when(loadRepo.sumCommittedHours(eq(c), any(), eq(Shipment.Mode.OCEAN), eq(d))).thenReturn(5.0);

        assertThat(service.getRemainingHoursOnDate(c, d, Shipment.Mode.OCEAN)).isEqualTo(7.0);   // 12 − 5
    }

    @Test
    void remainingNeverGoesNegative() {
        Carrier c = carrier(Shipment.Mode.OCEAN);
        LocalDate d = LocalDate.now();
        when(availRepo.findByCarrierAndDateAndMode(c, d, Shipment.Mode.OCEAN))
                .thenReturn(Optional.of(new CarrierAvailability(c, d, Shipment.Mode.OCEAN, 4.0)));
        when(loadRepo.sumCommittedHours(eq(c), any(), eq(Shipment.Mode.OCEAN), eq(d))).thenReturn(10.0);

        assertThat(service.getRemainingHoursOnDate(c, d, Shipment.Mode.OCEAN)).isEqualTo(0.0);   // clamped
    }

    @Test
    void undeclaredModeHasZeroRemaining() {
        Carrier c = carrier(Shipment.Mode.OCEAN);   // never declared ROAD availability
        LocalDate d = LocalDate.now();
        // findByCarrierAndDateAndMode unstubbed → Optional.empty → declared 0, committed 0
        assertThat(service.getRemainingHoursOnDate(c, d, Shipment.Mode.ROAD)).isEqualTo(0.0);
    }

    // ---- the duty clocks themselves ----

    @Test
    void clocksAreSeparatePerModeEachWithItsOwnCeiling() {
        Carrier c = carrier(Shipment.Mode.ROAD, Shipment.Mode.OCEAN);
        LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate fortStart = weekStart.minusWeeks(1);
        LocalDate weekEnd = weekStart.plusDays(6);

        // declared this week: ROAD 9h Mon + OCEAN 12h Tue
        when(availRepo.findByCarrierAndDateBetween(c, fortStart, weekEnd)).thenReturn(List.of(
                new CarrierAvailability(c, weekStart, Shipment.Mode.ROAD, 9.0),
                new CarrierAvailability(c, weekStart.plusDays(1), Shipment.Mode.OCEAN, 12.0)));
        // committed: 4h of OCEAN already assigned (both windows)
        when(loadRepo.sumCommittedHoursByMode(eq(c), any(), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{Shipment.Mode.OCEAN, 4.0}));

        List<DutyClock> clocks = service.getDutyClocks(c, weekStart);
        assertThat(clocks).extracting(DutyClock::getMode).containsExactly("ROAD", "OCEAN");

        DutyClock road = clocks.get(0);
        assertThat(road.getRegulation()).contains("561");
        assertThat(road.getMaxWeeklyHours()).isEqualTo(56.0);
        assertThat(road.getDeclaredThisWeek()).isEqualTo(9.0);
        assertThat(road.getCommittedThisWeek()).isEqualTo(0.0);
        assertThat(road.getRemainingThisWeek()).isEqualTo(9.0);          // min(56, 9) − 0

        DutyClock ocean = clocks.get(1);
        assertThat(ocean.getRegulation()).contains("STCW");
        assertThat(ocean.getMaxWeeklyHours()).isEqualTo(91.0);            // not the road 56
        assertThat(ocean.getDeclaredThisWeek()).isEqualTo(12.0);
        assertThat(ocean.getCommittedThisWeek()).isEqualTo(4.0);
        assertThat(ocean.getRemainingThisWeek()).isEqualTo(8.0);          // min(91, 12) − 4
    }

    @Test
    void roadOnlyCarrierGetsASingleRoadClock() {
        Carrier c = new Carrier();      // empty supportedModes = road-only
        c.setId(2L);
        LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        assertThat(service.getDutyClocks(c, weekStart))
                .extracting(DutyClock::getMode).containsExactly("ROAD");
    }

    // ---- browse pre-filter (getMatchingLoads) is per-mode + nets committed ----

    @Test
    void remainingByModeAndDateIsPerModeAndNetsCommitted() {
        Carrier c = carrier(Shipment.Mode.ROAD, Shipment.Mode.OCEAN);
        LocalDate d = LocalDate.now();
        when(availRepo.findByCarrierAndDateIn(eq(c), any())).thenReturn(List.of(
                new CarrierAvailability(c, d, Shipment.Mode.ROAD, 9.0),
                new CarrierAvailability(c, d, Shipment.Mode.OCEAN, 12.0)));
        when(loadRepo.sumCommittedHoursByDateAndMode(eq(c), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{d, Shipment.Mode.OCEAN, 5.0}));

        var byMode = service.getRemainingHoursByModeAndDate(c, Set.of(d));
        assertThat(byMode.get(Shipment.Mode.ROAD).get(d)).isEqualTo(9.0);    // road clock untouched
        assertThat(byMode.get(Shipment.Mode.OCEAN).get(d)).isEqualTo(7.0);   // 12 declared − 5 committed
    }
}
