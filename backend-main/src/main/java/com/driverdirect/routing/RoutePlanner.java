package com.driverdirect.routing;

import com.driverdirect.model.Shipment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Entry point for the routing engine (README.md, "Proposed: multimodal
 * routing engine"). Build-order steps 3–5: a time-dependent label-setting
 * search returning the Pareto-best options on <strong>(cost, CO2)</strong>,
 * with the arrival deadline a hard filter (never an optimisation axis). When
 * nothing satisfies the deadline it reruns for speed alone and returns the
 * single fastest-possible option (its arrival being past the deadline is the
 * caller's signal). When the query carries a {@code latestHandover} after
 * {@code earliestReady}, the search runs once per candidate handover day and
 * merges across days, keeping the latest viable handover per distinct route
 * (step 5, the flexible window — see {@link #findOptions}).
 *
 * <p>Mechanics: labels ({@link Label}) expand from the origin at
 * {@code earliestReady} (start of day, origin zone). Scheduled edges come
 * from the graph; virtual road edges are generated on demand — always to
 * the query destination, plus feeder legs to terminals with onward service
 * within {@link #ROAD_FEEDER_RADIUS_KM}. A mode change (or boarding a
 * scheduled service after arriving by any mode) requires a
 * {@link TransferProfile} at that location (cost + dwell); none means no
 * interchange there. Waiting for a later departure is inherent in
 * {@code nextDeparture}, so dwell is a first-class branch. Deadline pruning
 * is A*-style: current arrival plus great-circle distance at
 * {@link #FASTEST_MODE_KPH} must still make the deadline.
 *
 * <p>Dominance during search is <strong>exact</strong> Pareto on
 * {@code (cost, co2, arrivalTime)} per {@code (location, arrivalMode)}
 * bucket (tolerance {@code 0} — see {@link #DOMINANCE_TOLERANCE}), so the
 * collected front is the true Pareto set and no near-cheapest route is lost.
 * The design's ~5% near-duplicate tolerance is applied <em>only</em> to the
 * final returned front, as a non-compounding ε-grid thinner
 * ({@link #OUTPUT_EPSILON}, capped at {@link #MAX_OPTIONS}) — trimming the
 * output can't corrupt search optimality the way an ε-tolerant kill
 * direction would.
 */
public class RoutePlanner {

    // Search tunables — code-level constants for now (the PricingPolicy
    // pattern); promote to config only when something needs to vary them.
    static final double ROAD_AVG_SPEED_KPH = 70;
    /** Great-circle → driving-distance estimate for virtual road legs
     *  (typical road circuity; see the note on RoutingGraphBuilder's
     *  distance computation). */
    static final double ROAD_CIRCUITY = 1.3;
    static final double ROAD_FEEDER_RADIUS_KM = 300;
    /** Admissible A* bound: no cargo moves faster than a jet. */
    static final double FASTEST_MODE_KPH = 900;
    /** Exact Pareto dominance in the search frontier — a non-zero tolerance
     *  here lets an up-to-ε-pricier earlier label evict the true cheapest and
     *  the error compounds along a kill chain. Near-duplicate trimming lives
     *  on the output instead ({@link #OUTPUT_EPSILON}). */
    static final double DOMINANCE_TOLERANCE = 0.0;
    /** Near-duplicate trimming of the returned front (README's ~5%), applied
     *  once via an ε-grid — non-compounding, unlike a search-frontier ε. */
    static final double OUTPUT_EPSILON = 0.05;
    /** Cap on returned options (README: 3–6 Pareto-best). */
    static final int MAX_OPTIONS = 6;
    /** Guard against pathological graphs — a search this size normally pops
     *  a few hundred labels. */
    static final int MAX_LABEL_POPS = 50_000;
    /** Don't generate road legs between (near-)coincident locations. */
    static final double MIN_ROAD_KM = 0.1;
    /** Upper bound on query dates — deterministic (no clock coupling) and far
     *  enough out to never constrain a real query, but well below the ranges
     *  where LocalDate.plusDays / Instant.plus arithmetic overflow. Keeps an
     *  out-of-range date a 400, consistent with the other input validation. */
    static final LocalDate MAX_PLAN_DATE = LocalDate.of(4000, 1, 1);
    /** Cap on candidate handover days in a flexible-window search (README:
     *  "a 30-day window ≈ 30 fast searches"). A longer window is capped (and
     *  logged) rather than silently run unbounded. */
    static final int MAX_WINDOW_DAYS = 30;

    private static final Logger log = LoggerFactory.getLogger(RoutePlanner.class);

    private final RoutingGraph graph;
    /** Candidate edges are invariant per origin-location given the fixed query
     *  destination — memoise across the two searches (Pareto, then fastest)
     *  of one findOptions call. RoadEdge/ScheduledServiceEdge are immutable,
     *  so sharing them across labels is safe. */
    private final Map<Long, List<ServiceEdge>> candidateCache = new HashMap<>();

    public RoutePlanner(RoutingGraph graph) {
        this.graph = graph;
    }

    /**
     * The Pareto-best options on (cost, CO2) that satisfy the deadline, cost
     * ascending; or, when nothing does, a single fastest-possible option; or
     * an empty list when the destination is unreachable. Arrival any time on
     * the deadline day counts as meeting it.
     *
     * <p>Flexible window (step 5): when the query carries a {@code
     * latestHandover} after {@code earliestReady}, the search runs once per
     * candidate handover day and merges across days, keeping the <em>latest
     * viable handover</em> for each distinct route (the shipper holds cargo
     * as long as possible in a free warehouse rather than dwelling at a paid
     * terminal). A single-day query (no window) is just the one-element case.
     */
    public List<RouteOption> findOptions(RouteQuery query) {
        LocationNode origin = require(query.originLocationId(), "origin");
        LocationNode destination = require(query.destinationLocationId(), "destination");
        if (origin.id().equals(destination.id())) {
            throw new IllegalArgumentException("Origin and destination must differ");
        }
        if (query.earliestReady() == null) {
            throw new IllegalArgumentException("earliestReady is required");
        }
        if (afterHorizon(query.earliestReady()) || afterHorizon(query.latestHandover())
                || afterHorizon(query.arrivalDeadline())) {
            throw new IllegalArgumentException("Query dates must be before " + MAX_PLAN_DATE);
        }
        if (query.latestHandover() != null && query.latestHandover().isBefore(query.earliestReady())) {
            throw new IllegalArgumentException("latestHandover is before earliestReady");
        }
        Instant deadline = query.arrivalDeadline() != null
                ? query.arrivalDeadline().plusDays(1).atStartOfDay(destination.zone()).toInstant()
                : null;

        // One search per candidate handover day; a route found on several days
        // is the same leg sequence catching a different departure — merged to
        // its latest viable handover below.
        List<RouteOption> collected = new ArrayList<>();
        for (LocalDate day : candidateDays(query.earliestReady(), query.latestHandover())) {
            Instant start = day.atStartOfDay(origin.zone()).toInstant();
            for (Label label : searchParetoFront(query.cargo(), origin, destination, start, deadline)) {
                collected.add(toOption(label, start));
            }
        }
        if (!collected.isEmpty()) {
            return finalizeFront(mergeLatestHandoverPerRoute(collected));
        }
        if (deadline == null) {
            return List.of(); // unreachable across the whole window, not just late
        }
        // Nothing satisfies the deadline — report the single fastest achievable
        // arrival. It's reached from the earliest handover (you can't arrive
        // sooner by handing over later), so one search suffices.
        Instant earliest = query.earliestReady().atStartOfDay(origin.zone()).toInstant();
        return searchParetoFront(query.cargo(), origin, destination, earliest, null).stream()
                .map(label -> toOption(label, earliest))
                .min(Comparator.comparing(RouteOption::arrival).thenComparingDouble(RouteOption::totalCost))
                .map(List::of)
                .orElse(List.of());
    }

    /** Candidate handover days: {@code [earliest .. min(latest, earliest +
     *  MAX_WINDOW_DAYS-1)]}, or just {@code [earliest]} with no usable window.
     *  A window longer than the cap is truncated and logged, never run
     *  unbounded. */
    private List<LocalDate> candidateDays(LocalDate earliest, LocalDate latest) {
        if (latest == null || !latest.isAfter(earliest)) {
            return List.of(earliest);
        }
        LocalDate cap = earliest.plusDays(MAX_WINDOW_DAYS - 1);
        LocalDate end = latest.isAfter(cap) ? cap : latest;
        if (latest.isAfter(cap)) {
            log.warn("Flexible window {}..{} exceeds {} days; capping candidate handover days at {}",
                    earliest, latest, MAX_WINDOW_DAYS, end);
        }
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate day = earliest; !day.isAfter(end); day = day.plusDays(1)) {
            days.add(day);
        }
        return days;
    }

    /** Collapse per-day options of the same route (leg sequence) to a single
     *  entry with the latest handover — same cost/CO2 either way, so a later
     *  departure that still meets the deadline is strictly more flexible. */
    private List<RouteOption> mergeLatestHandoverPerRoute(List<RouteOption> options) {
        Map<String, RouteOption> byRoute = new LinkedHashMap<>();
        for (RouteOption option : options) {
            String key = routeKey(option.legs());
            RouteOption kept = byRoute.get(key);
            if (kept == null || option.handoverBy().isAfter(kept.handoverBy())) {
                byRoute.put(key, option);
            }
        }
        return new ArrayList<>(byRoute.values());
    }

    private static String routeKey(List<ServiceEdge> legs) {
        StringBuilder key = new StringBuilder();
        for (ServiceEdge edge : legs) {
            key.append(edge.originLocationId()).append('>').append(edge.destinationLocationId())
                    .append(':').append(edge.mode()).append('|');
        }
        return key.toString();
    }

    /** Bucket for dominance: labels are only comparable at the same location
     *  with the same arrival mode (future transfer cost depends on it).
     *  {@code mode} is null for the seed label. */
    private record Bucket(Long locationId, Shipment.Mode mode) {}

    /**
     * The exact Pareto front (on cost, co2, arrivalTime) of labels reaching
     * the destination. Drains the queue rather than stopping at the first
     * destination pop — completeness of a multi-criteria front needs every
     * non-dominated label, and exact per-bucket dominance keeps that bounded.
     */
    private List<Label> searchParetoFront(CargoDetails cargo, LocationNode origin,
                                          LocationNode destination, Instant start, Instant deadline) {
        PriorityQueue<Label> open = new PriorityQueue<>(
                Comparator.comparingDouble(Label::cost).thenComparing(Label::arrivalTime));
        Map<Bucket, List<Label>> frontier = new HashMap<>();
        List<Label> destinationFront = new ArrayList<>();
        Label seed = new Label(origin.id(), null, start, 0, 0, null, null);
        open.add(seed);
        frontier.computeIfAbsent(new Bucket(seed.locationId(), seed.arrivalMode()),
                k -> new ArrayList<>()).add(seed);

        int pops = 0;
        while (!open.isEmpty()) {
            if (++pops > MAX_LABEL_POPS) {
                // Backstop tripped: report it rather than let a truncated
                // search masquerade as an unreachable destination.
                log.warn("Route search hit the {}-label cap from {} to {}; front may be incomplete",
                        MAX_LABEL_POPS, origin.id(), destination.id());
                break;
            }
            Label current = open.poll();
            if (isDominated(frontier, current)) continue; // superseded since queued
            if (current.locationId().equals(destination.id())) {
                collectDestination(destinationFront, current); // sink: record, don't expand
                continue;
            }
            for (ServiceEdge edge : candidateEdges(current, destination)) {
                relax(current, edge, cargo, destination, deadline, open, frontier);
            }
        }
        return destinationFront;
    }

    /** Keep the destination front exactly Pareto-non-dominated. */
    private void collectDestination(List<Label> front, Label candidate) {
        for (Label existing : front) {
            if (existing.dominates(candidate, DOMINANCE_TOLERANCE)) return;
        }
        front.removeIf(existing -> candidate.dominates(existing, DOMINANCE_TOLERANCE));
        front.add(candidate);
    }

    private void relax(Label current, ServiceEdge edge, CargoDetails cargo,
                       LocationNode destination, Instant deadline,
                       PriorityQueue<Label> open, Map<Bucket, List<Label>> frontier) {
        double transferCost = 0;
        Instant ready = current.arrivalTime();
        // A terminal interchange is needed whenever we change mode OR board a
        // scheduled service after arriving by any mode — two vessels/trains/
        // flights meeting at a hub is a real transshipment (handling + dwell),
        // not a free instant connection. Road→road stays free: it's one truck
        // driving on (RoadEdge is never a ScheduledServiceEdge).
        boolean modeChange = current.arrivalMode() != null && edge.mode() != current.arrivalMode();
        boolean scheduledInterchange = current.arrivalMode() != null && edge instanceof ScheduledServiceEdge;
        if (modeChange || scheduledInterchange) {
            TransferProfile transfer = graph.transferProfile(
                    current.locationId(), current.arrivalMode(), edge.mode());
            if (transfer == null) return; // no interchange possible here
            transferCost = transfer.cost();
            ready = ready.plus(Duration.ofMinutes((long) transfer.dwellMinutes()));
        }
        Instant departure = edge.nextDeparture(ready);
        if (departure == null) return;
        Instant arrival = edge.arrivalTime(departure);
        if (deadline != null && !arrival.isBefore(deadline)) return;
        if (deadline != null
                && !lowerBoundArrival(edge.destinationLocationId(), destination, arrival)
                        .isBefore(deadline)) {
            return; // even a jet from here can't make the deadline
        }

        Label next = new Label(edge.destinationLocationId(), edge.mode(), arrival,
                current.cost() + transferCost + edge.cost(cargo),
                current.co2() + edge.co2(cargo),
                current, edge);
        List<Label> bucket = frontier.computeIfAbsent(
                new Bucket(next.locationId(), next.arrivalMode()), k -> new ArrayList<>());
        for (Label existing : bucket) {
            if (existing.dominates(next, DOMINANCE_TOLERANCE)) return;
        }
        bucket.removeIf(existing -> next.dominates(existing, DOMINANCE_TOLERANCE));
        bucket.add(next);
        open.add(next);
    }

    /** Scheduled edges from the graph plus on-demand virtual road legs:
     *  always one to the query destination, plus feeders to in-radius
     *  terminals that have onward scheduled service. */
    private List<ServiceEdge> candidateEdges(Label current, LocationNode destination) {
        return candidateCache.computeIfAbsent(current.locationId(), locationId -> {
            List<ServiceEdge> edges = new ArrayList<>(graph.edgesFrom(locationId));
            LocationNode here = graph.location(locationId);
            if (here == null || !here.hasCoordinates()) return edges;
            addRoadEdge(edges, here, destination);
            for (LocationNode node : graph.locations().values()) {
                if (node.id().equals(here.id()) || node.id().equals(destination.id())) continue;
                if (!node.hasCoordinates()) continue;
                if (graph.edgesFrom(node.id()).isEmpty()) continue; // a feeder must feed something
                if (here.greatCircleKm(node) > ROAD_FEEDER_RADIUS_KM) continue;
                addRoadEdge(edges, here, node);
            }
            return edges;
        });
    }

    private void addRoadEdge(List<ServiceEdge> edges, LocationNode from, LocationNode to) {
        if (!to.hasCoordinates()) return;
        double drivingKm = from.greatCircleKm(to) * ROAD_CIRCUITY;
        if (drivingKm < MIN_ROAD_KM) return;
        edges.add(new RoadEdge(from.id(), to.id(), drivingKm, ROAD_AVG_SPEED_KPH, graph.roadRates()));
    }

    private Instant lowerBoundArrival(Long fromId, LocationNode destination, Instant at) {
        LocationNode from = graph.location(fromId);
        if (from == null || !from.hasCoordinates() || !destination.hasCoordinates()) {
            return at; // no coordinates — can't bound, don't prune
        }
        double km = from.greatCircleKm(destination);
        return at.plus(Duration.ofMinutes((long) (km / FASTEST_MODE_KPH * 60)));
    }

    private boolean isDominated(Map<Bucket, List<Label>> frontier, Label label) {
        for (Label existing : frontier.getOrDefault(
                new Bucket(label.locationId(), label.arrivalMode()), List.of())) {
            if (existing != label && existing.dominates(label, DOMINANCE_TOLERANCE)) return true;
        }
        return false;
    }

    /** Project the merged options onto their (cost, CO2) Pareto front and thin
     *  near-duplicates — cost ascending. */
    private List<RouteOption> finalizeFront(List<RouteOption> options) {
        return thinFront(costCo2Front(options));
    }

    /** The (cost, CO2) Pareto staircase, cost ascending, keeping the
     *  latest-handover representative of each (cost, co2) point (time is not
     *  an output axis — only ever a hard filter — so a later handover is the
     *  tiebreak, matching the flexible-window "hold cargo as long as
     *  possible" preference). */
    static List<RouteOption> costCo2Front(List<RouteOption> options) {
        List<RouteOption> sorted = new ArrayList<>(options);
        sorted.sort(Comparator.comparingDouble(RouteOption::totalCost)
                .thenComparingDouble(RouteOption::totalCo2)
                .thenComparing(Comparator.comparing(RouteOption::handoverBy).reversed()));
        List<RouteOption> result = new ArrayList<>();
        double minCo2 = Double.POSITIVE_INFINITY;
        for (RouteOption option : sorted) {
            // Cost-ascending: keep an option only when it is strictly greener
            // than everything at least as cheap — the lower staircase.
            if (option.totalCo2() < minCo2 - 1e-9) {
                result.add(option);
                minCo2 = option.totalCo2();
            }
        }
        return result;
    }

    /** Non-compounding near-duplicate trimming: keep one representative per
     *  ε-grid cell (README's ~5%), then, if still over {@link #MAX_OPTIONS},
     *  evenly sample the cost-sorted front keeping both endpoints (cheapest +
     *  greenest). Input must be the cost-ascending (cost, CO2) front. */
    static List<RouteOption> thinFront(List<RouteOption> costSortedFront) {
        if (costSortedFront.size() <= 1) return costSortedFront;
        record Cell(long cost, long co2) {}
        List<RouteOption> deduped = new ArrayList<>();
        Set<Cell> seen = new HashSet<>();
        for (RouteOption option : costSortedFront) {
            if (seen.add(new Cell(gridCell(option.totalCost()), gridCell(option.totalCo2())))) {
                deduped.add(option);
            }
        }
        if (deduped.size() <= MAX_OPTIONS) return deduped;
        List<RouteOption> capped = new ArrayList<>();
        int n = deduped.size();
        long last = -1;
        for (int i = 0; i < MAX_OPTIONS; i++) {
            long idx = Math.round((double) i * (n - 1) / (MAX_OPTIONS - 1));
            if (idx != last) {
                capped.add(deduped.get((int) idx));
                last = idx;
            }
        }
        return capped;
    }

    /** ε-grid bucket index for a positive value; a shared floor for
     *  non-positive values (0-CO2 legs) so they collapse together. */
    private static long gridCell(double value) {
        if (value <= 0) return Long.MIN_VALUE;
        return (long) Math.floor(Math.log(value) / Math.log(1 + OUTPUT_EPSILON));
    }

    private RouteOption toOption(Label winning, Instant start) {
        Deque<ServiceEdge> legs = new ArrayDeque<>();
        for (Label label = winning; label.edgeTaken() != null; label = label.parent()) {
            legs.addFirst(label.edgeTaken());
        }
        List<ServiceEdge> legList = List.copyOf(legs);
        // When the first leg of this plan actually departs (no transfer
        // precedes it) — the cargo's handover/departure instant for this
        // option. The flexible-window loop keeps the latest such instant per
        // route across candidate days, which is the "latest viable handover".
        Instant handoverBy = legList.get(0).nextDeparture(start);
        return new RouteOption(legList, winning.cost(), winning.co2(), handoverBy,
                winning.arrivalTime());
    }

    private static boolean afterHorizon(LocalDate date) {
        return date != null && date.isAfter(MAX_PLAN_DATE);
    }

    private LocationNode require(Long locationId, String which) {
        LocationNode node = locationId != null ? graph.location(locationId) : null;
        if (node == null) {
            throw new IllegalArgumentException("Unknown " + which + " location: " + locationId);
        }
        return node;
    }
}
