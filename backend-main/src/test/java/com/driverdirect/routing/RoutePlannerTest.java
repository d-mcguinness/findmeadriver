package com.driverdirect.routing;

import com.driverdirect.model.ChargeUnit;
import com.driverdirect.model.Location;
import com.driverdirect.model.Shipment;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The step-3 search on hand-built graphs: cheapest time-dependent option
 * with deadline pruning, virtual road legs (direct + feeder), transfers
 * gated by TransferProfile, waiting for scheduled departures, and the
 * fastest-possible fallback when nothing meets the deadline.
 *
 * <p>Geography: an origin address ~5 km from Dublin Port, a daily/weekly
 * sailing to Rotterdam, and a destination address ~25 km beyond it. Road
 * minimum charges make the intermodal totals exact (feeder legs are under
 * the ~83 km where PER_KM overtakes the €150 minimum).
 */
class RoutePlannerTest {

    private static final Tariff ROAD = new Tariff(ChargeUnit.PER_KM, 50, 1.20, 150);
    /** Deliberately cheap sailing so intermodal beats direct road on cost. */
    private static final Tariff CHEAP_OCEAN = new Tariff(ChargeUnit.PER_CONTAINER, 0, 10, 10);
    private static final CargoDetails ONE_CONTAINER = new CargoDetails(null, null, 1, null);
    /** 2026-07-06 is a Monday; start = 00:00 Dublin = 2026-07-05T23:00Z. */
    private static final LocalDate MONDAY = LocalDate.of(2026, 7, 6);
    private static final Instant START = Instant.parse("2026-07-05T23:00:00Z");

    private static LocationNode node(long id, Location.LocationType type,
                                     Double lat, Double lon, String zone) {
        return new LocationNode(id, "L" + id, type, "IE", lat, lon, ZoneId.of(zone));
    }

    private final LocationNode origin = node(1, Location.LocationType.ADDRESS, 53.35, -6.26, "Europe/Dublin");
    private final LocationNode dublinPort = node(2, Location.LocationType.SEAPORT, 53.34, -6.20, "Europe/Dublin");
    private final LocationNode rotterdam = node(3, Location.LocationType.SEAPORT, 51.95, 4.14, "Europe/Amsterdam");
    private final LocationNode destination = node(4, Location.LocationType.ADDRESS, 51.92, 4.48, "Europe/Amsterdam");

    private ScheduledServiceEdge sailing(EnumSet<DayOfWeek> days) {
        return new ScheduledServiceEdge(2L, 3L, Shipment.Mode.OCEAN, days,
                LocalTime.of(8, 0), ZoneId.of("Europe/Dublin"), Duration.ofHours(24),
                718.0, CHEAP_OCEAN);
    }

    private RoutingGraph intermodalGraph(EnumSet<DayOfWeek> sailingDays) {
        return new RoutingGraph(
                Map.of(2L, List.of(sailing(sailingDays))),
                Map.of(2L, List.of(new TransferProfile(2L, Shipment.Mode.ROAD, Shipment.Mode.OCEAN, 50, 60)),
                        3L, List.of(new TransferProfile(3L, Shipment.Mode.OCEAN, Shipment.Mode.ROAD, 50, 60))),
                Map.of(1L, origin, 2L, dublinPort, 3L, rotterdam, 4L, destination),
                ROAD);
    }

    @Test
    void assemblesTheCheapestIntermodalRoute() {
        RoutePlanner planner = new RoutePlanner(intermodalGraph(EnumSet.allOf(DayOfWeek.class)));
        List<RouteOption> options = planner.findOptions(
                new RouteQuery(1L, 4L, ONE_CONTAINER, MONDAY, null, null));

        assertThat(options).hasSize(1);
        RouteOption option = options.get(0);
        assertThat(option.legs()).extracting(ServiceEdge::mode).containsExactly(
                Shipment.Mode.ROAD, Shipment.Mode.OCEAN, Shipment.Mode.ROAD);
        // 150 (feeder min charge) + 50 (ROAD→OCEAN) + 10 (sailing)
        //   + 50 (OCEAN→ROAD) + 150 (last-mile min charge)
        assertThat(option.totalCost()).isEqualTo(410.0);
        // handoverBy = first (road) leg's departure = the search start.
        assertThat(option.handoverBy()).isEqualTo(START);
        // Sails Monday 08:00 Dublin (07:00Z), lands Tuesday 07:00Z, then
        // dwell + a short road leg.
        assertThat(option.arrival())
                .isAfter(Instant.parse("2026-07-07T07:00:00Z"))
                .isBefore(Instant.parse("2026-07-07T10:00:00Z"));
    }

    @Test
    void deadlineForcesThePricierButFasterRoad() {
        RoutePlanner planner = new RoutePlanner(intermodalGraph(EnumSet.allOf(DayOfWeek.class)));
        // Must arrive Monday: the Tuesday-landing sailing is pruned, direct
        // road (~14 h) still makes it despite costing ~3× more.
        List<RouteOption> options = planner.findOptions(
                new RouteQuery(1L, 4L, ONE_CONTAINER, MONDAY, null, MONDAY));

        assertThat(options).hasSize(1);
        RouteOption option = options.get(0);
        assertThat(option.legs()).extracting(ServiceEdge::mode).containsExactly(Shipment.Mode.ROAD);
        assertThat(option.totalCost()).isGreaterThan(1000.0);
        // End of Monday in the destination zone (Europe/Amsterdam, +02:00).
        assertThat(option.arrival()).isBefore(Instant.parse("2026-07-06T22:00:00Z"));
    }

    @Test
    void arrivalLateOnTheDeadlineDayStillCounts() {
        // The sailing lands Tuesday 07:00Z and the last-mile road leg pushes
        // arrival to ~07:26Z — late in the day, but ON the deadline day. The
        // plusDays(1) grace must keep the cheap intermodal option rather than
        // pruning the sailing and falling back to expensive direct road.
        RoutePlanner planner = new RoutePlanner(intermodalGraph(EnumSet.allOf(DayOfWeek.class)));
        LocalDate tuesday = LocalDate.of(2026, 7, 7);
        List<RouteOption> options = planner.findOptions(
                new RouteQuery(1L, 4L, ONE_CONTAINER, MONDAY, null, tuesday));

        assertThat(options).hasSize(1);
        RouteOption option = options.get(0);
        assertThat(option.legs()).extracting(ServiceEdge::mode).containsExactly(
                Shipment.Mode.ROAD, Shipment.Mode.OCEAN, Shipment.Mode.ROAD);
        assertThat(option.totalCost()).isEqualTo(410.0);
        assertThat(option.arrival()).isAfter(Instant.parse("2026-07-07T07:00:00Z"));
    }

    @Test
    void sameModeInterchangeAtASharedHubIsCostedNotFree() {
        // Two OCEAN sailings meet at Rotterdam: Dublin→Rotterdam then
        // Rotterdam→Hamburg. Boarding the second vessel is a real vessel-to-
        // vessel transshipment — it must consult a transfer profile (cost +
        // dwell), not board free at the exact arrival instant.
        // No coordinates on Hamburg → no virtual road edge can reach it, so
        // it's reachable ONLY via the two sailings and their hub interchange.
        LocationNode hamburg = new LocationNode(5L, "Hamburg", Location.LocationType.SEAPORT,
                "DE", null, null, ZoneId.of("Europe/Berlin"));
        ScheduledServiceEdge leg1 = sailing(EnumSet.allOf(DayOfWeek.class)); // 2→3, 08:00 Dublin, 24h
        ScheduledServiceEdge leg2 = new ScheduledServiceEdge(3L, 5L, Shipment.Mode.OCEAN,
                EnumSet.allOf(DayOfWeek.class), LocalTime.of(9, 0), ZoneId.of("Europe/Amsterdam"),
                Duration.ofHours(24), 480.0, CHEAP_OCEAN);

        // With the OCEAN→OCEAN interchange profile present it routes, and the
        // second leg can't depart at the arrival instant — it waits for the
        // next 09:00 sailing after the dwell.
        RoutingGraph withProfile = new RoutingGraph(
                Map.of(2L, List.of(leg1), 3L, List.of(leg2)),
                Map.of(3L, List.of(new TransferProfile(3L, Shipment.Mode.OCEAN, Shipment.Mode.OCEAN, 150, 360))),
                Map.of(2L, dublinPort, 3L, rotterdam, 5L, hamburg),
                ROAD);
        List<RouteOption> routed = new RoutePlanner(withProfile).findOptions(
                new RouteQuery(2L, 5L, ONE_CONTAINER, MONDAY, null, null));
        assertThat(routed).hasSize(1);
        // 10 (leg1) + 150 (transshipment) + 10 (leg2)
        assertThat(routed.get(0).totalCost()).isEqualTo(170.0);

        // Without any transfer profile at the hub the interchange is
        // impossible, so Hamburg is unreachable.
        RoutingGraph noProfile = new RoutingGraph(
                Map.of(2L, List.of(leg1), 3L, List.of(leg2)), Map.of(),
                Map.of(2L, dublinPort, 3L, rotterdam, 5L, hamburg), ROAD);
        assertThat(new RoutePlanner(noProfile).findOptions(
                new RouteQuery(2L, 5L, ONE_CONTAINER, MONDAY, null, null))).isEmpty();
    }

    @Test
    void impossibleDeadlineFallsBackToFastestPossible() {
        // Destination has no coordinates → no road access, and the only
        // sailing is Thursday: nothing can arrive by Monday.
        LocationNode portNoCoords = new LocationNode(3L, "L3", Location.LocationType.SEAPORT,
                "NL", null, null, ZoneId.of("Europe/Amsterdam"));
        RoutingGraph graph = new RoutingGraph(
                Map.of(2L, List.of(sailing(EnumSet.of(DayOfWeek.THURSDAY)))),
                Map.of(),
                Map.of(2L, dublinPort, 3L, portNoCoords),
                ROAD);
        RoutePlanner planner = new RoutePlanner(graph);

        List<RouteOption> options = planner.findOptions(
                new RouteQuery(2L, 3L, ONE_CONTAINER, MONDAY, null, MONDAY));

        assertThat(options).hasSize(1);
        RouteOption option = options.get(0);
        // Waits for Thursday's 08:00 Dublin departure (dwell is a branch,
        // not a failure), lands Friday — after the deadline, which is the
        // caller's signal that the deadline is unachievable.
        assertThat(option.handoverBy()).isEqualTo(Instant.parse("2026-07-09T07:00:00Z"));
        assertThat(option.arrival()).isEqualTo(Instant.parse("2026-07-10T07:00:00Z"));
        assertThat(option.legs()).extracting(ServiceEdge::mode).containsExactly(Shipment.Mode.OCEAN);
    }

    @Test
    void modeChangeWithoutATransferProfileIsImpossible() {
        // Same shape as the intermodal graph but no transfer profiles at
        // all: the sailing exists but can never be boarded after a road
        // arrival, so only the direct road option comes back.
        RoutingGraph graph = new RoutingGraph(
                Map.of(2L, List.of(sailing(EnumSet.allOf(DayOfWeek.class)))),
                Map.of(),
                Map.of(1L, origin, 2L, dublinPort, 3L, rotterdam, 4L, destination),
                ROAD);
        RoutePlanner planner = new RoutePlanner(graph);

        List<RouteOption> options = planner.findOptions(
                new RouteQuery(1L, 4L, ONE_CONTAINER, MONDAY, null, null));

        assertThat(options).hasSize(1);
        assertThat(options.get(0).legs()).extracting(ServiceEdge::mode)
                .containsExactly(Shipment.Mode.ROAD);
    }

    @Test
    void unreachableDestinationWithoutDeadlineReturnsEmpty() {
        LocationNode isolatedA = new LocationNode(1L, "L1", Location.LocationType.ADDRESS,
                "IE", null, null, ZoneId.of("Europe/Dublin"));
        LocationNode isolatedB = new LocationNode(2L, "L2", Location.LocationType.ADDRESS,
                "NL", null, null, ZoneId.of("Europe/Amsterdam"));
        RoutePlanner planner = new RoutePlanner(new RoutingGraph(
                Map.of(), Map.of(), Map.of(1L, isolatedA, 2L, isolatedB), ROAD));

        assertThat(planner.findOptions(new RouteQuery(1L, 2L, ONE_CONTAINER, MONDAY, null, null)))
                .isEmpty();
    }

    @Test
    void rejectsUnusableQueries() {
        RoutePlanner planner = new RoutePlanner(intermodalGraph(EnumSet.allOf(DayOfWeek.class)));
        assertThatThrownBy(() -> planner.findOptions(
                new RouteQuery(99L, 4L, ONE_CONTAINER, MONDAY, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("origin");
        assertThatThrownBy(() -> planner.findOptions(
                new RouteQuery(1L, 1L, ONE_CONTAINER, MONDAY, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("differ");
        assertThatThrownBy(() -> planner.findOptions(
                new RouteQuery(1L, 4L, ONE_CONTAINER, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("earliestReady");
    }
}
