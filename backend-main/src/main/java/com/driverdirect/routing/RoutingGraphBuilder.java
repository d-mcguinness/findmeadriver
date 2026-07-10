package com.driverdirect.routing;

import com.driverdirect.model.CarrierLane;
import com.driverdirect.model.Location;
import com.driverdirect.model.Shipment;
import com.driverdirect.repository.CarrierLaneRepository;
import com.driverdirect.repository.LocationRepository;
import com.driverdirect.service.EmissionPolicy;
import com.driverdirect.service.PricingPolicy;
import com.driverdirect.service.TransferPolicy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds a {@link RoutingGraph} snapshot from the relational tables:
 * timetabled {@link CarrierLane}s with terminal anchors become
 * {@link ScheduledServiceEdge}s; every {@link Location} becomes a
 * {@link LocationNode} (zone resolved here, UTC fallback); rate cards are
 * compiled to {@link Tariff}s. Constant query count — one for locations, one
 * fetch-joined query for lanes — never per-lane lookups.
 *
 * <p>There is deliberately no cache, no scheduled rebuild and no
 * invalidation machinery here: a build is a few indexed reads plus map
 * assembly (milliseconds at this network's size), so
 * {@code RoutePlannerService} builds a fresh graph per route query and
 * freshness is perfect by construction. If route volume ever makes that
 * matter (~50+ QPS or builds beyond ~100 ms), the named escape hatch is a
 * short-TTL memoisation around {@link #build()} — still no scheduler, still
 * correct with multiple app instances (see README.md, "Proposed: multimodal
 * routing engine").
 */
@Component
@RequiredArgsConstructor
public class RoutingGraphBuilder {

    private static final Logger log = LoggerFactory.getLogger(RoutingGraphBuilder.class);

    private final CarrierLaneRepository carrierLaneRepository;
    private final LocationRepository locationRepository;
    private final PricingPolicy pricingPolicy;
    private final TransferPolicy transferPolicy;
    private final EmissionPolicy emissionPolicy;

    /** Read-only marks the build one non-flushing transaction. Note it does
     *  NOT make the two reads one consistent snapshot — they run at
     *  READ_COMMITTED, so a lane committing between them can reference a
     *  location the earlier read didn't see; the location-map guard in
     *  {@link #toEdge} (not the transaction) is what keeps that harmless. */
    @Transactional(readOnly = true)
    public RoutingGraph build() {
        Map<Long, LocationNode> locations = locationRepository.findAll().stream()
                .collect(Collectors.toUnmodifiableMap(Location::getId, LocationNode::from));

        Map<Long, List<ServiceEdge>> edges = new HashMap<>();
        for (CarrierLane lane : carrierLaneRepository.findTimetabledWithTerminals()) {
            ScheduledServiceEdge edge = toEdge(lane, locations);
            if (edge != null) {
                edges.computeIfAbsent(edge.originLocationId(), k -> new ArrayList<>()).add(edge);
            }
        }

        // Transfer profiles have no table yet (build-order step 2's second
        // half), so every typed terminal gets the TransferPolicy code
        // defaults compiled in — without them "no profile = no transfer
        // possible" would block every mode change and the search could never
        // assemble an intermodal route. RoutingGraph's compact constructor
        // deep-freezes the maps and lists.
        return new RoutingGraph(edges, defaultTransferProfiles(locations), locations,
                LegRates.from(pricingPolicy.rateCardFor(Shipment.Mode.ROAD),
                        emissionPolicy.kgCo2ePerTonneKm(Shipment.Mode.ROAD)));
    }

    /** TransferPolicy defaults per typed terminal: every ordered pair of the
     *  modes that terminal type handles, plus same-mode interchange for the
     *  scheduled modes (RAIL/OCEAN/AIR) so a vessel→vessel / train→train
     *  transshipment at a shared hub is costed like any other. ROAD self-pair
     *  is skipped — road→road is one truck driving on, never an interchange.
     *  ADDRESS locations get nothing. */
    private Map<Long, List<TransferProfile>> defaultTransferProfiles(Map<Long, LocationNode> locations) {
        Map<Long, List<TransferProfile>> byLocation = new HashMap<>();
        for (LocationNode node : locations.values()) {
            Set<Shipment.Mode> modes = transferPolicy.modesHandledAt(node.type());
            if (modes.size() < 2) continue;
            List<TransferProfile> profiles = new ArrayList<>();
            for (Shipment.Mode from : modes) {
                for (Shipment.Mode to : modes) {
                    if (from == to && from == Shipment.Mode.ROAD) continue;
                    profiles.add(new TransferProfile(node.id(), from, to,
                            transferPolicy.transferCost(from, to),
                            transferPolicy.transferDwellMinutes(from, to)));
                }
            }
            byLocation.put(node.id(), profiles);
        }
        return byLocation;
    }

    /** One timetabled lane → one scheduled edge; null (logged) when the lane
     *  can't back an edge. Fail-soft like CarrierLane.getDepartureDaySet —
     *  one bad lane must not take the whole graph down. */
    private ScheduledServiceEdge toEdge(CarrierLane lane, Map<Long, LocationNode> locations) {
        if (!lane.isTimetabled()) return null; // blank-string days the query can't filter out
        Set<DayOfWeek> days = lane.getDepartureDaySet();
        if (days.isEmpty()) {
            log.warn("Skipping lane {}: no parseable departure days '{}'", lane.getId(), lane.getDepartureDays());
            return null;
        }
        PricingPolicy.RateCard card = pricingPolicy.rateCardFor(lane.getServiceMode());
        if (card == null) {
            log.warn("Skipping lane {}: no rate card for service mode {}", lane.getId(), lane.getServiceMode());
            return null;
        }
        double transitHours = lane.getTransitDurationHours();
        if (!Double.isFinite(transitHours) || transitHours <= 0
                || transitHours > CarrierLane.MAX_TRANSIT_HOURS) {
            // The API validates this on write, but a stored row that bypassed
            // it would overflow Duration arithmetic (or mint a zero-transit
            // edge from NaN) and take the whole build down.
            log.warn("Skipping lane {}: unusable transitDurationHours {}", lane.getId(), transitHours);
            return null;
        }
        LocationNode origin = locations.get(lane.getOriginLocation().getId());
        LocationNode destination = locations.get(lane.getDestinationLocation().getId());
        if (origin == null || destination == null) {
            // A lane committed between build()'s two reads can anchor to a
            // location the earlier findAll() didn't see — skip it; the next
            // query's build picks it up.
            log.warn("Skipping lane {}: terminal location not in this build's location snapshot", lane.getId());
            return null;
        }

        // Great-circle is a *lower bound* on road distance: PricingService's
        // PER_KM basis meters the stored route km (Routes-API driving
        // distance), typically 20–40% longer. The search must treat PER_KM
        // edge costs as optimistic estimates; scheduled RAIL/OCEAN/AIR
        // tariffs don't meter distance, so only timetabled ROAD lanes and
        // virtual RoadEdges carry this bias.
        double distanceKm = origin.hasCoordinates() && destination.hasCoordinates()
                ? origin.greatCircleKm(destination)
                : 0.0; // unknown — PER_KM tariffs then fall back to their minimum charge
        return new ScheduledServiceEdge(
                origin.id(), destination.id(), lane.getServiceMode(),
                days, lane.getDepartureTime(), origin.zone(),
                // Floor at 1 minute: a zero-duration edge would let the
                // search chain hops without advancing time.
                Duration.ofMinutes(Math.max(1, Math.round(transitHours * 60))),
                distanceKm,
                LegRates.from(card, emissionPolicy.kgCo2ePerTonneKm(lane.getServiceMode())));
    }
}
