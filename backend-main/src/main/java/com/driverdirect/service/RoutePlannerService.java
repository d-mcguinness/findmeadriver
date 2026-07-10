package com.driverdirect.service;

import com.driverdirect.routing.RouteOption;
import com.driverdirect.routing.RoutePlanner;
import com.driverdirect.routing.RouteQuery;
import com.driverdirect.routing.RoutingGraph;
import com.driverdirect.routing.RoutingGraphBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

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

    public List<RouteOption> findOptions(RouteQuery query) {
        RoutingGraph graph = graphBuilder.build();
        return new RoutePlanner(graph).findOptions(query);
    }
}
