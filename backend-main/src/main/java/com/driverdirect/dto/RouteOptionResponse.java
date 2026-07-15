package com.driverdirect.dto;

import com.driverdirect.routing.RouteOption;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * One proposed door-to-door route — a Pareto-best point on (cost, CO2) — as
 * returned to a shipper. {@code totalCost} is carrier cost in the query's
 * implied currency (estimate; the authoritative price is re-computed by
 * PricingService when a leg is accepted and posted as a Load).
 * {@code totalCo2Kg} is kg CO2e. {@code handoverBy} is when this plan's first
 * leg departs; {@code arrival} when it reaches the destination.
 */
@Data
public class RouteOptionResponse {

    private double totalCost;
    private double totalCo2Kg;
    private Instant handoverBy;
    private Instant arrival;
    /** False only on the fastest-possible fallback returned when no route met
     *  the deadline — the deadline verdict computed in the destination zone,
     *  so the client needn't (and can't correctly) re-derive it from UTC. */
    private boolean meetsDeadline;
    private List<RouteLegResponse> legs;

    public static RouteOptionResponse from(RouteOption option, Map<Long, String> locationNames) {
        RouteOptionResponse r = new RouteOptionResponse();
        r.setTotalCost(option.totalCost());
        r.setTotalCo2Kg(option.totalCo2());
        r.setHandoverBy(option.handoverBy());
        r.setArrival(option.arrival());
        r.setMeetsDeadline(option.meetsDeadline());
        r.setLegs(option.legs().stream()
                .map(edge -> RouteLegResponse.from(edge, locationNames))
                .collect(Collectors.toList()));
        return r;
    }
}
