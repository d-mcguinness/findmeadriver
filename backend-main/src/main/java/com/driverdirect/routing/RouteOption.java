package com.driverdirect.routing;

import java.time.Instant;
import java.util.List;

/**
 * One finalized, Pareto-best candidate — reconstructed by walking a winning
 * {@link Label}'s parent chain. {@code legs} is a display/acceptance view;
 * accepting an option still goes through the existing
 * CreateIntermodalLoadRequest-shaped submission, unchanged (README.md,
 * "Integration point").
 *
 * <p><strong>Money.</strong> {@code totalCost} is what the shipper pays — the
 * search's objective — broken out into {@code carrierCostTotal} (what the
 * carriers earn), {@code commissionTotal} (each leg's fee at <em>its own</em>
 * mode's rate, since commission runs ROAD 10% … AIR 20%) and
 * {@code transferCostTotal} (terminal handling at interchanges). The three sum
 * to {@code totalCost}. Accepting this option materialises legs that
 * PricingService re-prices and {@code HandlingCharge} rows it derives from the
 * same {@code TransferPolicy} rates, so the Itinerary's grand total reconciles
 * with {@code totalCost} — quote and bill agree on all three components.
 *
 * <p>{@code handoverBy} is the departure instant of this plan's first leg.
 * For a flexible-window query (step 5) the planner keeps, per route, the
 * option whose first leg departs latest while still meeting the deadline —
 * the "latest viable handover" (cargo held as long as possible). Note this
 * is the first-leg <em>departure</em>: for a scheduled first leg it is the
 * service's departure, which can fall after the shipper's handover day
 * (cargo waits at the terminal).
 */
public record RouteOption(
        List<ServiceEdge> legs,
        double totalCost,
        double carrierCostTotal,
        double commissionTotal,
        double transferCostTotal,
        double totalCo2,
        Instant handoverBy,
        Instant arrival,
        boolean meetsDeadline) {
}
