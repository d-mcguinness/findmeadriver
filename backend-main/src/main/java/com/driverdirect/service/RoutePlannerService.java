package com.driverdirect.service;

import com.driverdirect.dto.AcceptRouteRequest;
import com.driverdirect.dto.CreateIntermodalLoadRequest;
import com.driverdirect.dto.CreateLegRequest;
import com.driverdirect.dto.RouteOptionResponse;
import com.driverdirect.model.ChargeUnit;
import com.driverdirect.model.Location;
import com.driverdirect.model.Shipment;
import com.driverdirect.model.Shipper;
import com.driverdirect.repository.LocationRepository;
import com.driverdirect.routing.LocationNode;
import com.driverdirect.routing.RouteOption;
import com.driverdirect.routing.RoutePlanner;
import com.driverdirect.routing.RouteQuery;
import com.driverdirect.routing.RoutingGraph;
import com.driverdirect.routing.RoutingGraphBuilder;
import com.driverdirect.routing.ServiceEdge;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
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
 * <p>{@link RoutePlanner#findOptions} runs the build-order step 3–5 search:
 * the Pareto-best options on (cost, CO2) that satisfy the deadline (looping
 * candidate handover days when a flexible window is given and keeping the
 * latest viable handover per route), or the single fastest-possible option
 * when the deadline can't be met (its arrival past the deadline is the
 * caller's signal).
 */
@Service
@RequiredArgsConstructor
public class RoutePlannerService {

    private final RoutingGraphBuilder graphBuilder;
    private final LocationRepository locationRepository;
    private final PricingPolicy pricingPolicy;

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

    /**
     * Materialise an accepted route into an intermodal-create request (the
     * "integration point"): re-plan the query (tenant-scoped), match the
     * client's selected leg sequence to a <em>current</em> option, and map
     * that server-computed option's legs — mode, endpoint names/countries,
     * and distance — onto {@link CreateIntermodalLoadRequest}, carrying the
     * query's cargo onto each leg so it re-prices on its mode's basis.
     * Re-planning (not trusting the client's legs) guarantees only a route
     * the engine actually proposed can be accepted; the caller then hands the
     * result to {@code LoadService.createIntermodalLoad} unchanged.
     *
     * <p>Note: per-leg carrier costs are re-priced by PricingService, so the
     * itinerary total tracks the estimate's leg costs but omits the terminal
     * transfer costs the estimate included — transfers aren't billable Loads
     * in this model.
     */
    public CreateIntermodalLoadRequest buildAcceptedItinerary(AcceptRouteRequest accept, Shipper shipper) {
        if (accept.getLegs() == null || accept.getLegs().isEmpty()) {
            throw new IllegalArgumentException("Select a route (its legs) to accept");
        }
        RouteQuery query = accept.toQuery();
        requireAccessible(query.originLocationId(), shipper);
        requireAccessible(query.destinationLocationId(), shipper);

        Planned p = planned(query);
        RouteOption chosen = p.options().stream()
                .filter(option -> legsMatch(option.legs(), accept.getLegs()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "That route is no longer available — re-plan and accept a current option"));
        // Booking creates real priced Loads, so the cargo must carry the
        // quantity each chosen leg's mode meters — otherwise PricingService
        // can't price it and the leg would post at €0. The propose estimate
        // tolerates sparse cargo (it floors to the card minimum); acceptance
        // does not. Reject with a clear ask rather than invent a quantity.
        for (ServiceEdge edge : chosen.legs()) {
            requireCargoForLeg(edge, accept);
        }
        return toIntermodalRequest(chosen, accept, p.graph());
    }

    private void requireCargoForLeg(ServiceEdge edge, AcceptRouteRequest accept) {
        PricingPolicy.RateCard card = pricingPolicy.rateCardFor(edge.mode());
        if (card == null) return; // no rate card → hourly fallback path, nothing to require
        ChargeUnit unit = card.unit();
        Shipment.Mode mode = edge.mode();
        switch (unit) {
            case PER_CONTAINER:
                if (accept.getContainerCount() == null || accept.getContainerCount() <= 0) {
                    throw new IllegalArgumentException("This route has a " + mode
                            + " leg priced per container — provide containerCount to accept");
                }
                break;
            case PER_PIECE:
                if (accept.getPieceCount() == null || accept.getPieceCount() <= 0) {
                    throw new IllegalArgumentException("This route has a " + mode
                            + " leg priced per piece — provide pieceCount to accept");
                }
                break;
            case PER_CHARGEABLE_KG:
                if (accept.getWeightKg() == null && accept.getVolumeM3() == null) {
                    throw new IllegalArgumentException("This route has a " + mode
                            + " leg priced per chargeable-kg — provide weightKg (or volumeM3) to accept");
                }
                break;
            case PER_KM:
                if (edge.distanceKm() <= 0) {
                    throw new IllegalArgumentException("The " + mode
                            + " leg has no known distance and can't be priced — re-plan");
                }
                break;
            default: // PER_HOUR / FLAT — no metered quantity required
                break;
        }
    }

    /** An option matches iff its edges are the same (origin, destination,
     *  mode) sequence the client selected. */
    private boolean legsMatch(List<ServiceEdge> edges, List<AcceptRouteRequest.AcceptedLeg> selector) {
        if (edges.size() != selector.size()) return false;
        for (int i = 0; i < edges.size(); i++) {
            ServiceEdge edge = edges.get(i);
            AcceptRouteRequest.AcceptedLeg leg = selector.get(i);
            if (!edge.originLocationId().equals(leg.getOriginLocationId())
                    || !edge.destinationLocationId().equals(leg.getDestinationLocationId())
                    || edge.mode() == null || leg.getMode() == null
                    || !edge.mode().name().equalsIgnoreCase(leg.getMode().trim())) {
                return false;
            }
        }
        return true;
    }

    private CreateIntermodalLoadRequest toIntermodalRequest(
            RouteOption chosen, AcceptRouteRequest accept, RoutingGraph graph) {
        CreateIntermodalLoadRequest request = new CreateIntermodalLoadRequest();
        request.setTitle(accept.getTitle() != null && !accept.getTitle().isBlank()
                ? accept.getTitle() : defaultTitle(chosen, graph));
        request.setDescription(accept.getDescription());
        request.setDateNeeded(accept.getEarliestReady()); // the handover day is authoritative
        request.setEarliestReadyDate(accept.getEarliestReady());
        request.setLatestHandoverDate(accept.getLatestHandover());
        request.setArrivalDeadline(accept.getArrivalDeadline());
        request.setCurrency(accept.getCurrency());

        List<CreateLegRequest> legs = new ArrayList<>();
        for (ServiceEdge edge : chosen.legs()) {
            LocationNode from = graph.location(edge.originLocationId());
            LocationNode to = graph.location(edge.destinationLocationId());
            CreateLegRequest leg = new CreateLegRequest();
            leg.setTransportMode(edge.mode() != null ? edge.mode().name() : null);
            leg.setPickupLocation(from != null ? from.name() : null);
            leg.setPickupCountry(from != null ? from.country() : null);
            leg.setDeliveryLocation(to != null ? to.name() : null);
            leg.setDeliveryCountry(to != null ? to.country() : null);
            leg.setDistanceKm(BigDecimal.valueOf(edge.distanceKm()));
            leg.setWeightKg(accept.getWeightKg());
            leg.setVolumeM3(accept.getVolumeM3());
            leg.setContainerCount(accept.getContainerCount());
            leg.setPieceCount(accept.getPieceCount());
            legs.add(leg);
        }
        request.setLegs(legs);
        return request;
    }

    private String defaultTitle(RouteOption chosen, RoutingGraph graph) {
        LocationNode from = graph.location(chosen.legs().get(0).originLocationId());
        LocationNode to = graph.location(chosen.legs().get(chosen.legs().size() - 1).destinationLocationId());
        return "Proposed route: " + (from != null ? from.name() : "?")
                + " → " + (to != null ? to.name() : "?");
    }
}
