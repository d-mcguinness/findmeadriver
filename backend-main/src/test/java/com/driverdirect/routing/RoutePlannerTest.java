package com.driverdirect.routing;

import com.driverdirect.model.ChargeUnit;
import com.driverdirect.model.Location;
import com.driverdirect.model.Shipment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * The step-3/4 search on hand-built graphs: the cheapest time-dependent
 * option with deadline pruning, the (cost, CO2) Pareto front when options
 * genuinely trade off, virtual road legs (direct + feeder), transfers gated
 * by TransferProfile (incl. same-mode hub interchange), waiting for
 * scheduled departures, and the fastest-possible fallback.
 *
 * <p>Geography: an origin address ~5 km from Dublin Port, a daily/weekly
 * sailing to Rotterdam, and a destination address ~25 km beyond it. Road
 * minimum charges make the intermodal totals exact (feeder legs are under
 * the ~83 km where PER_KM overtakes the €150 minimum).
 */
class RoutePlannerTest {

    private static final LegRates ROAD =
            new LegRates(new Tariff(ChargeUnit.PER_KM, 50, 1.20, 150), 0.075);
    /** Deliberately cheap sailing so intermodal beats direct road on cost. */
    private static final LegRates CHEAP_OCEAN =
            new LegRates(new Tariff(ChargeUnit.PER_CONTAINER, 0, 10, 10), 0.012);
    private static final CargoDetails ONE_CONTAINER = new CargoDetails(null, null, 1, null);
    /** 20 tonnes in one container — weight makes CO2 (and the Pareto front) real. */
    private static final CargoDetails LADEN = new CargoDetails(BigDecimal.valueOf(20000), null, 1, null);
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
    void returnsTheCostCo2ParetoFrontWhenOptionsTradeOff() {
        // Two direct sailings-of-different-mode between one port pair: RAIL is
        // cheaper but dirtier, OCEAN pricier but greener — a genuine two-point
        // (cost, CO2) front, both boarded from the seed (no transfer needed).
        ScheduledServiceEdge rail = new ScheduledServiceEdge(1L, 2L, Shipment.Mode.RAIL,
                EnumSet.allOf(DayOfWeek.class), LocalTime.of(8, 0), ZoneId.of("Europe/Dublin"),
                Duration.ofHours(12), 800.0,
                new LegRates(new Tariff(ChargeUnit.PER_CONTAINER, 0, 600, 600), 0.025));
        ScheduledServiceEdge ocean = new ScheduledServiceEdge(1L, 2L, Shipment.Mode.OCEAN,
                EnumSet.allOf(DayOfWeek.class), LocalTime.of(8, 0), ZoneId.of("Europe/Dublin"),
                Duration.ofHours(24), 800.0,
                new LegRates(new Tariff(ChargeUnit.PER_CONTAINER, 350, 1800, 1800), 0.012));
        LocationNode portA = node(1, Location.LocationType.SEAPORT, 53.34, -6.20, "Europe/Dublin");
        LocationNode portB = node(2, Location.LocationType.SEAPORT, 51.95, 4.14, "Europe/Amsterdam");
        RoutingGraph graph = new RoutingGraph(
                Map.of(1L, List.of(rail, ocean)), Map.of(),
                Map.of(1L, portA, 2L, portB), ROAD);

        List<RouteOption> options = new RoutePlanner(graph).findOptions(
                new RouteQuery(1L, 2L, LADEN, MONDAY, null, null));

        // Cost ascending: cheap-dirty RAIL, then pricey-green OCEAN. The direct
        // road option (pricier AND dirtier than rail) is Pareto-dominated out.
        assertThat(options).hasSize(2);
        assertThat(options).extracting(RouteOption::legs)
                .allSatisfy(legs -> assertThat(legs).hasSize(1));
        assertThat(options.get(0).legs().get(0).mode()).isEqualTo(Shipment.Mode.RAIL);
        assertThat(options.get(0).totalCost()).isEqualTo(600.0);
        assertThat(options.get(0).totalCo2()).isCloseTo(800 * 20 * 0.025, within(0.01)); // 400
        assertThat(options.get(1).legs().get(0).mode()).isEqualTo(Shipment.Mode.OCEAN);
        assertThat(options.get(1).totalCost()).isEqualTo(2150.0);
        assertThat(options.get(1).totalCo2()).isCloseTo(800 * 20 * 0.012, within(0.01)); // 192
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

    // ---- Output projection + thinning (package-private statics) ----

    private static Label label(double cost, double co2) {
        return new Label(2L, Shipment.Mode.OCEAN, Instant.parse("2026-07-06T00:00:00Z"),
                cost, co2, null, null);
    }

    @Test
    void costCo2FrontKeepsTheParetoStaircase() {
        Label cheap = label(600, 400);
        Label dominated = label(1000, 500); // pricier AND dirtier than cheap
        Label green = label(2150, 192);
        List<Label> front = RoutePlanner.costCo2Front(List.of(dominated, green, cheap));
        assertThat(front).containsExactly(cheap, green); // dominated dropped, cost-sorted
    }

    @Test
    void thinFrontDropsEpsilonNearDuplicates() {
        Label a = label(600, 400);
        Label b = label(610, 398); // within 5% on both axes → same ε-grid cell
        assertThat(RoutePlanner.thinFront(List.of(a, b))).containsExactly(a);
    }

    @Test
    void thinFrontCapsAtSixKeepingEndpoints() {
        List<Label> front = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            front.add(label(100 * (i + 1), 1000 - 100 * i)); // 10 well-separated points
        }
        List<Label> thinned = RoutePlanner.thinFront(front);
        assertThat(thinned).hasSize(RoutePlanner.MAX_OPTIONS);
        assertThat(thinned.get(0)).isEqualTo(front.get(0)); // cheapest kept
        assertThat(thinned.get(thinned.size() - 1)).isEqualTo(front.get(9)); // greenest kept
    }
}
