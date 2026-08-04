package com.driverdirect.dto;

import com.driverdirect.routing.RouteOption;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * One proposed door-to-door route — a Pareto-best point on (cost, CO2) — as
 * returned to a shipper. {@code totalCost} is <strong>what the shipper
 * pays</strong> in the query's implied currency, broken out below; it is not
 * carrier cost, because commission varies by mode (ROAD 10% … AIR 20%) and a
 * shipper comparing routes is comparing what they are billed. Still an
 * estimate: PricingService re-computes the authoritative price when a leg is
 * accepted and posted as a Load. {@code totalCo2Kg} is kg CO2e.
 * {@code handoverBy} is when this plan's first leg departs; {@code arrival}
 * when it reaches the destination.
 */
@Data
public class RouteOptionResponse {

    /** What the shipper pays: carrierCostTotal + commissionTotal +
     *  transferCostTotal. The search's objective. */
    private double totalCost;
    /** What the carriers earn across the legs. */
    private double carrierCostTotal;
    /** Platform fee, each leg at its own mode's rate. */
    private double commissionTotal;
    /** Terminal handling at interchanges. Billed on acceptance as
     *  {@code HandlingCharge} rows off the same TransferPolicy rates, and
     *  included in the Itinerary's handlingTotal — so this quote reconciles
     *  with the bill. Uncommissioned: a pass-through terminal charge. */
    private double transferCostTotal;
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
        r.setCarrierCostTotal(option.carrierCostTotal());
        r.setCommissionTotal(option.commissionTotal());
        r.setTransferCostTotal(option.transferCostTotal());
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
