package com.driverdirect.service;

import com.driverdirect.dto.RouteOptionResponse;
import com.driverdirect.model.Location;
import com.driverdirect.model.Shipper;
import com.driverdirect.repository.LocationRepository;
import com.driverdirect.routing.RouteOption;
import com.driverdirect.routing.RoutePlanner;
import com.driverdirect.routing.RouteQuery;
import com.driverdirect.routing.RoutingGraph;
import com.driverdirect.routing.RoutingGraphBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Entry seam for the routing engine: builds a fresh {@link RoutingGraph}
 * per query (build-per-query — the deliberate populate strategy, see
 * README.md "Proposed: multimodal routing engine").
 *
 * <p>The graph is a method-local snapshot on the request thread: no cache,
 * no scheduled rebuild, no invalidation events, no cross-instance
 * coordination. A carrier who saves a timetable sees it in the very next
 * route query, and all searches of one query share one consistent world.
 * This is correct-by-construction because a build costs milliseconds at
 * this network's size; the named escape hatch if that ever changes is a
 * short-TTL memoisation here — not a cron.
 *
 * <p>{@link RoutePlanner#findOptions} runs the build-order step 3–4 search:
 * the Pareto-best options on (cost, CO2) that satisfy the deadline for one
 * handover day, or the single fastest-possible option when the deadline
 * can't be met (its arrival past the deadline is the caller's signal). The
 * flexible-window per-day loop is step 5.
 */
@Service
@RequiredArgsConstructor
public class RoutePlannerService {

    private final RoutingGraphBuilder graphBuilder;
    private final LocationRepository locationRepository;

    /** Low-level entry: the raw Pareto front. Internal/test callers; the
     *  controllers use {@link #planRoutes}. */
    public List<RouteOption> findOptions(RouteQuery query) {
        return planned(query).options();
    }

    /**
     * Shipper-facing entry: same as {@link #planRoutes} but tenant-scoped —
     * a shipper may only route between <em>public reference nodes</em>
     * (typed ports/airports/rail terminals) and locations they own. Any
     * other id (another tenant's ad-hoc ADDRESS pickup/delivery site) is
     * rejected exactly like an unknown id, so the endpoint can't be used to
     * resolve/enumerate other shippers' private location names. The
     * unrestricted {@link #planRoutes} is admin-only (cross-tenant by design).
     */
    public List<RouteOptionResponse> planRoutesForShipper(RouteQuery query, Shipper shipper) {
        requireAccessible(query.originLocationId(), shipper);
        requireAccessible(query.destinationLocationId(), shipper);
        return planRoutes(query);
    }

    private void requireAccessible(Long locationId, Shipper shipper) {
        if (locationId == null) return; // the planner's own require() 400s on null
        Location loc = locationRepository.findById(locationId).orElse(null);
        if (loc == null) return; // let the planner 400 it as unknown (uniform message)
        boolean publicNode = loc.getLocationType() != null
                && loc.getLocationType() != Location.LocationType.ADDRESS;
        boolean ownedByCaller = loc.getOwnerShipper() != null
                && loc.getOwnerShipper().getId().equals(shipper.getId());
        if (!publicNode && !ownedByCaller) {
            // Same shape as an unknown id — never reveal existence or the name.
            throw new IllegalArgumentException("Unknown location: " + locationId);
        }
    }

    /** API-facing entry: the Pareto front mapped to response DTOs, with leg
     *  location names resolved from the same snapshot the options were
     *  planned against (no extra queries — the graph already holds them). */
    public List<RouteOptionResponse> planRoutes(RouteQuery query) {
        Planned p = planned(query);
        Map<Long, String> names = p.graph().locations().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().name()));
        return p.options().stream()
                .map(option -> RouteOptionResponse.from(option, names))
                .collect(Collectors.toList());
    }

    /** One graph build shared by the plan and its DTO mapping. */
    private Planned planned(RouteQuery query) {
        RoutingGraph graph = graphBuilder.build();
        return new Planned(graph, new RoutePlanner(graph).findOptions(query));
    }

    private record Planned(RoutingGraph graph, List<RouteOption> options) {}
}
