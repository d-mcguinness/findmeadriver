package com.driverdirect.routing;

import com.driverdirect.model.Shipment;

import java.time.Instant;

/**
 * One partial (or complete) route in the label-setting search. A predecessor
 * chain rather than a copied leg list — path reconstruction (walking
 * {@code parent}) only happens for the handful of labels that survive to
 * become a final option, not for every label the search creates.
 *
 * <p>{@code cost}/{@code co2} are primitive doubles, not BigDecimal — this is
 * an objective function compared many times during search, not the final
 * financial figure. Only a winning option's legs get snapshotted onto real
 * Shipment/Load BigDecimal fields, exactly like PricingService already does
 * at its own boundary.
 */
public record Label(
        Long locationId,
        Shipment.Mode arrivalMode,
        Instant arrivalTime,
        double cost,
        double co2,
        Label parent,
        ServiceEdge edgeTaken) {

    /**
     * True if this label is at least as good as {@code other} on every
     * criterion (arrival time exactly; cost/co2 within {@code tolerance}) and
     * strictly better on at least one — i.e. {@code other} is redundant and
     * can be discarded. Callers must only compare labels within the same
     * {@code (locationId, arrivalMode)} bucket: future transfer cost depends
     * on arrival mode, so labels that differ on it aren't safely comparable
     * even at the same location.
     */
    public boolean dominates(Label other, double tolerance) {
        boolean timeOk = !arrivalTime.isAfter(other.arrivalTime);
        boolean costOk = cost <= other.cost * (1 + tolerance);
        boolean co2Ok = co2 <= other.co2 * (1 + tolerance);
        boolean strictlyBetter = arrivalTime.isBefore(other.arrivalTime)
                || cost < other.cost * (1 - tolerance)
                || co2 < other.co2 * (1 - tolerance);
        return timeOk && costOk && co2Ok && strictlyBetter;
    }
}
