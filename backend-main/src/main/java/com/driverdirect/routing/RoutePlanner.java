package com.driverdirect.routing;

import com.driverdirect.model.Shipment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Entry point for the routing engine (README.md, "Proposed: multimodal
 * routing engine"). This is build-order step 3's search: a time-dependent
 * label-setting search over one handover day, returning the single cheapest
 * option that satisfies the arrival deadline — or, when nothing does, the
 * fastest-possible option instead (its arrival being past the deadline is
 * the caller's signal). Two-criteria (cost, CO2) Pareto is step 4; the
 * flexible-window per-day loop and cross-day merge is step 5 —
 * {@code latestHandover} is accepted but not yet explored.
 *
 * <p>Mechanics: labels ({@link Label}) expand from the origin at
 * {@code earliestReady} (start of day, origin zone). Scheduled edges come
 * from the graph; virtual road edges are generated on demand — always to
 * the query destination (door-to-door trucking has no schedule), plus
 * feeder legs to terminals with onward service within
 * {@link #ROAD_FEEDER_RADIUS_KM}. A mode change requires a
 * {@link TransferProfile} at that location (cost + dwell); none means no
 * transfer there. Waiting for a later departure is inherent in
 * {@code nextDeparture}, so dwell is a first-class branch, not friction.
 * Deadline pruning is A*-style: current arrival plus great-circle distance
 * at {@link #FASTEST_MODE_KPH} must still make the deadline. Dominance is
 * per {@code (location, arrivalMode)} bucket and is exact Pareto on
 * {@code (cost, arrivalTime)} (tolerance {@code 0}), so the single option
 * returned is genuinely cost-optimal. The design's ~5% near-duplicate
 * tolerance belongs to step 4, where a Pareto <em>set</em> of options is
 * returned and duplicate trimming matters; applying it here would let an
 * up-to-5%-pricier earlier label evict the true cheapest, and the error
 * compounds along a kill chain — so v1 keeps it at 0.
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
    /** Exact Pareto dominance in v1 — see the class javadoc for why the
     *  design's ~5% is deferred to step 4's multi-option search. */
    static final double DOMINANCE_TOLERANCE = 0.0;
    /** Guard against pathological graphs (e.g. an unreachable destination
     *  with no coordinates to prune by) — a search this size normally pops
     *  a few hundred labels. */
    static final int MAX_LABEL_POPS = 50_000;
    /** Don't generate road legs between (near-)coincident locations. */
    static final double MIN_ROAD_KM = 0.1;

    private static final Logger log = LoggerFactory.getLogger(RoutePlanner.class);

    private final RoutingGraph graph;
    /** Candidate edges are invariant per origin-location given the fixed query
     *  destination — memoise across the two searches (cheapest, then fastest)
     *  of one findOptions call. RoadEdge/ScheduledServiceEdge are immutable,
     *  so sharing them across labels is safe. */
    private final Map<Long, List<ServiceEdge>> candidateCache = new HashMap<>();

    public RoutePlanner(RoutingGraph graph) {
        this.graph = graph;
    }

    /**
     * The cheapest deadline-satisfying option, or the fastest-possible
     * option when the deadline can't be met, or an empty list when the
     * destination is unreachable. Arrival any time on the deadline day
     * counts as meeting it.
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
        Instant start = query.earliestReady().atStartOfDay(origin.zone()).toInstant();
        Instant deadline = query.arrivalDeadline() != null
                ? query.arrivalDeadline().plusDays(1).atStartOfDay(destination.zone()).toInstant()
                : null;

        Label cheapest = search(query.cargo(), origin, destination, start, deadline,
                Comparator.comparingDouble(Label::cost).thenComparing(Label::arrivalTime));
        if (cheapest != null) {
            return List.of(toOption(cheapest, start));
        }
        if (deadline == null) {
            return List.of(); // unreachable, not just late
        }
        // Nothing satisfies the deadline — rerun optimising speed alone and
        // report the fastest possible arrival (README: the deadline is a
        // hard filter, but the shipper should learn what IS achievable).
        Label fastest = search(query.cargo(), origin, destination, start, null,
                Comparator.comparing(Label::arrivalTime).thenComparingDouble(Label::cost));
        return fastest == null ? List.of() : List.of(toOption(fastest, start));
    }

    /** Bucket for dominance: labels are only comparable at the same location
     *  with the same arrival mode (future transfer cost depends on it).
     *  {@code mode} is null for the seed label. */
    private record Bucket(Long locationId, Shipment.Mode mode) {}

    private Label search(CargoDetails cargo, LocationNode origin, LocationNode destination,
                         Instant start, Instant deadline, Comparator<Label> order) {
        PriorityQueue<Label> open = new PriorityQueue<>(order);
        Map<Bucket, List<Label>> frontier = new HashMap<>();
        Label seed = new Label(origin.id(), null, start, 0, 0, null, null);
        open.add(seed);
        frontier.computeIfAbsent(new Bucket(seed.locationId(), seed.arrivalMode()),
                k -> new ArrayList<>()).add(seed);

        int pops = 0;
        while (!open.isEmpty()) {
            if (++pops > MAX_LABEL_POPS) {
                // Backstop tripped: report it rather than let a truncated
                // search masquerade as an unreachable destination.
                log.warn("Route search hit the {}-label cap from {} to {}; result may be incomplete",
                        MAX_LABEL_POPS, origin.id(), destination.id());
                return null;
            }
            Label current = open.poll();
            // With a non-negative-cost (resp. non-decreasing-time) order, the
            // first destination pop is the winner, within tolerance.
            if (current.locationId().equals(destination.id())) return current;
            if (isDominated(frontier, current)) continue; // superseded since queued
            for (ServiceEdge edge : candidateEdges(current, destination)) {
                relax(current, edge, cargo, destination, deadline, open, frontier);
            }
        }
        return null;
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
        edges.add(new RoadEdge(from.id(), to.id(), drivingKm, ROAD_AVG_SPEED_KPH, graph.roadTariff()));
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

    private RouteOption toOption(Label winning, Instant start) {
        Deque<ServiceEdge> legs = new ArrayDeque<>();
        for (Label label = winning; label.edgeTaken() != null; label = label.parent()) {
            legs.addFirst(label.edgeTaken());
        }
        List<ServiceEdge> legList = List.copyOf(legs);
        // When the first leg of the computed plan actually departs — the
        // handover time for THIS option (no transfer precedes the first leg).
        // The design's "latest viable handover" (back-propagating slack so a
        // shipper can hold cargo in a free warehouse) is step-5 flexible-
        // window work; v1 reports the plan's own first departure.
        Instant handoverBy = legList.get(0).nextDeparture(start);
        return new RouteOption(legList, winning.cost(), winning.co2(), handoverBy,
                winning.arrivalTime());
    }

    private LocationNode require(Long locationId, String which) {
        LocationNode node = locationId != null ? graph.location(locationId) : null;
        if (node == null) {
            throw new IllegalArgumentException("Unknown " + which + " location: " + locationId);
        }
        return node;
    }
}
