package com.driverdirect.model;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Timetable behaviour on CarrierLane (routing-engine step 2): the
 * nextDeparture contract the future ScheduledServiceEdge delegates to.
 */
class CarrierLaneTest {

    private CarrierLane timetabled(String days, LocalTime time) {
        CarrierLane lane = new CarrierLane();
        lane.setDepartureDays(days);
        lane.setDepartureTime(time);
        lane.setTransitDurationHours(24.0);
        return lane;
    }

    @Test
    void untimetabledLaneHasNoDepartures() {
        CarrierLane lane = new CarrierLane();
        assertThat(lane.isTimetabled()).isFalse();
        assertThat(lane.nextDeparture(LocalDateTime.now())).isNull();
    }

    @Test
    void partialTimetableIsNotTimetabled() {
        CarrierLane lane = new CarrierLane();
        lane.setDepartureTime(LocalTime.NOON);
        assertThat(lane.isTimetabled()).isFalse();
        lane.setDepartureDays("MONDAY");
        assertThat(lane.isTimetabled()).isFalse(); // still no transit hours
        lane.setTransitDurationHours(12.0);
        assertThat(lane.isTimetabled()).isTrue();
    }

    @Test
    void sameDayDepartureWhenAskedBeforeDepartureTime() {
        // 2026-07-06 is a Monday.
        CarrierLane lane = timetabled("MONDAY,THURSDAY", LocalTime.of(14, 0));
        LocalDateTime mondayMorning = LocalDateTime.of(2026, 7, 6, 9, 0);
        assertThat(lane.nextDeparture(mondayMorning))
                .isEqualTo(LocalDateTime.of(2026, 7, 6, 14, 0));
    }

    @Test
    void rollsToNextScheduledDayWhenAskedAfterDepartureTime() {
        CarrierLane lane = timetabled("MONDAY,THURSDAY", LocalTime.of(14, 0));
        LocalDateTime mondayEvening = LocalDateTime.of(2026, 7, 6, 15, 0);
        assertThat(lane.nextDeparture(mondayEvening))
                .isEqualTo(LocalDateTime.of(2026, 7, 9, 14, 0)); // Thursday
    }

    @Test
    void departureExactlyAtQueryInstantCounts() {
        CarrierLane lane = timetabled("MONDAY", LocalTime.of(14, 0));
        LocalDateTime exactly = LocalDateTime.of(2026, 7, 6, 14, 0);
        assertThat(lane.nextDeparture(exactly)).isEqualTo(exactly);
    }

    @Test
    void wrapsAcrossTheWeekForASingleWeeklyDeparture() {
        CarrierLane lane = timetabled("MONDAY", LocalTime.of(14, 0));
        LocalDateTime tuesday = LocalDateTime.of(2026, 7, 7, 8, 0);
        assertThat(lane.nextDeparture(tuesday))
                .isEqualTo(LocalDateTime.of(2026, 7, 13, 14, 0)); // next Monday
    }

    @Test
    void unrecognisedStoredDayTokensAreSkippedNotThrown() {
        CarrierLane lane = timetabled("MONDAY, funday ,THURSDAY", LocalTime.NOON);
        assertThat(lane.getDepartureDaySet())
                .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.THURSDAY);
    }
}
