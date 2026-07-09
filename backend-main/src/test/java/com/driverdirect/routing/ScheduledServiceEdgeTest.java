package com.driverdirect.routing;

import com.driverdirect.model.ChargeUnit;
import com.driverdirect.model.Shipment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The pattern-on-edge contract: departures are resolved per occurrence from
 * (days, origin-local time, zone) at query time — the graph never holds a
 * materialised departure list, so there is no planning horizon to decay and
 * DST resolves correctly for every occurrence. The DST tests below are the
 * load-bearing ones: they fail under the naive alternative of resolving the
 * first departure to an Instant and stepping by 7 days.
 */
class ScheduledServiceEdgeTest {

    private static final ZoneId DUBLIN = ZoneId.of("Europe/Dublin");
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    private static final Tariff OCEAN_TARIFF =
            new Tariff(ChargeUnit.PER_CONTAINER, 350, 1800, 1800);

    private ScheduledServiceEdge edge(Set<DayOfWeek> days, LocalTime time, ZoneId zone) {
        return new ScheduledServiceEdge(1L, 2L, Shipment.Mode.OCEAN,
                days, time, zone, Duration.ofHours(36), 720.0, OCEAN_TARIFF);
    }

    @Test
    void sameDayDepartureWhenAskedBeforeLocalDepartureTime() {
        // 2026-07-06 is a Monday; Dublin is UTC+1 in July, so 14:00 local = 13:00Z.
        ScheduledServiceEdge e = edge(Set.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
                LocalTime.of(14, 0), DUBLIN);
        assertThat(e.nextDeparture(Instant.parse("2026-07-06T08:00:00Z")))
                .isEqualTo(Instant.parse("2026-07-06T13:00:00Z"));
    }

    @Test
    void missedDepartureRollsToNextPatternDay() {
        ScheduledServiceEdge e = edge(Set.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
                LocalTime.of(14, 0), DUBLIN);
        // Monday 15:00 local — Monday's sailing is gone, Thursday 2026-07-09 is next.
        assertThat(e.nextDeparture(Instant.parse("2026-07-06T14:00:00Z")))
                .isEqualTo(Instant.parse("2026-07-09T13:00:00Z"));
    }

    @Test
    void exactDepartureInstantIsBoardable() {
        ScheduledServiceEdge e = edge(Set.of(DayOfWeek.MONDAY), LocalTime.of(14, 0), DUBLIN);
        Instant departure = Instant.parse("2026-07-06T13:00:00Z");
        assertThat(e.nextDeparture(departure)).isEqualTo(departure);
    }

    @Test
    void wrapsToSameWeekdayNextWeek() {
        ScheduledServiceEdge e = edge(Set.of(DayOfWeek.MONDAY), LocalTime.of(14, 0), DUBLIN);
        assertThat(e.nextDeparture(Instant.parse("2026-07-06T14:00:00Z")))
                .isEqualTo(Instant.parse("2026-07-13T13:00:00Z"));
    }

    @Test
    void emptyPatternHasNoDeparture() {
        ScheduledServiceEdge e = edge(Set.of(), LocalTime.NOON, DUBLIN);
        assertThat(e.nextDeparture(Instant.parse("2026-07-06T08:00:00Z"))).isNull();
    }

    @Test
    void springForwardResolvesEachOccurrenceAtItsOwnOffset() {
        // Europe/Paris switches CET(+01:00) → CEST(+02:00) on Sunday 2026-03-29.
        // A weekly Sunday 08:00 departure is 07:00Z before the switch and
        // 06:00Z after — stepping the first Instant by 7 days would wrongly
        // give 07:00Z on the 29th too.
        ScheduledServiceEdge e = edge(Set.of(DayOfWeek.SUNDAY), LocalTime.of(8, 0), PARIS);
        assertThat(e.nextDeparture(Instant.parse("2026-03-16T00:00:00Z")))
                .isEqualTo(Instant.parse("2026-03-22T07:00:00Z"));
        assertThat(e.nextDeparture(Instant.parse("2026-03-23T00:00:00Z")))
                .isEqualTo(Instant.parse("2026-03-29T06:00:00Z"));
    }

    @Test
    void gapTimeOnSpringForwardDayShiftsForwardByTheGap() {
        // 02:30 local doesn't exist on 2026-03-29 (clocks jump 02:00 → 03:00);
        // java.time shifts it to 03:30 CEST = 01:30Z. Pinned as documented
        // behaviour rather than left to surprise.
        ScheduledServiceEdge e = edge(Set.of(DayOfWeek.SUNDAY), LocalTime.of(2, 30), PARIS);
        assertThat(e.nextDeparture(Instant.parse("2026-03-28T12:00:00Z")))
                .isEqualTo(Instant.parse("2026-03-29T01:30:00Z"));
    }

    @Test
    void overlapTimeOnFallBackDayTakesTheEarlierOffset() {
        // 02:30 local happens twice on 2026-10-25 (CEST +02:00, then CET +01:00);
        // java.time picks the earlier offset: 02:30+02:00 = 00:30Z.
        ScheduledServiceEdge e = edge(Set.of(DayOfWeek.SUNDAY), LocalTime.of(2, 30), PARIS);
        assertThat(e.nextDeparture(Instant.parse("2026-10-24T12:00:00Z")))
                .isEqualTo(Instant.parse("2026-10-25T00:30:00Z"));
    }

    @Test
    void arrivalAddsTransitDuration() {
        ScheduledServiceEdge e = edge(Set.of(DayOfWeek.MONDAY), LocalTime.of(14, 0), DUBLIN);
        assertThat(e.arrivalTime(Instant.parse("2026-07-06T13:00:00Z")))
                .isEqualTo(Instant.parse("2026-07-08T01:00:00Z"));
    }

    @Test
    void costDelegatesToTheCompiledTariff() {
        ScheduledServiceEdge e = edge(Set.of(DayOfWeek.MONDAY), LocalTime.of(14, 0), DUBLIN);
        CargoDetails twoContainers = new CargoDetails(null, null, 2, null);
        assertThat(e.cost(twoContainers)).isEqualTo(350 + 1800 * 2);
        // Missing the metered quantity → the minimum charge stands in as floor.
        CargoDetails unknown = new CargoDetails(BigDecimal.valueOf(500), null, null, null);
        assertThat(e.cost(unknown)).isEqualTo(1800);
    }

    @Test
    void utcZoneEdgeWorksWithoutARegionZone() {
        // Builder falls back to UTC when a Location declares no timezone.
        ScheduledServiceEdge e = edge(Set.of(DayOfWeek.MONDAY), LocalTime.of(14, 0), ZoneOffset.UTC);
        assertThat(e.nextDeparture(Instant.parse("2026-07-06T08:00:00Z")))
                .isEqualTo(Instant.parse("2026-07-06T14:00:00Z"));
    }
}
