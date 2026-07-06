package com.driverdirect.routing;

import com.driverdirect.model.Shipment;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * A rail/sea/air service backed by a real carrier lane's timetable.
 * {@code departures} is a placeholder shape for this skeleton — CarrierLane
 * now carries the recurring weekly schedule (build-order step 2 landed:
 * departureDays/departureTime/transitDurationHours + nextDeparture(after)),
 * so the graph build should construct these from timetabled lanes, expanding
 * or delegating to {@code CarrierLane.nextDeparture} rather than keeping a
 * materialised departure list.
 */
public record ScheduledServiceEdge(
        Long originLocationId,
        Long destinationLocationId,
        Shipment.Mode mode,
        List<Instant> departures,
        Duration transitDuration,
        double baseCost,
        double costPerKg,
        double co2PerKg) implements ServiceEdge {

    @Override
    public Instant nextDeparture(Instant after) {
        return departures.stream()
                .filter(d -> !d.isBefore(after))
                .findFirst()
                .orElse(null);
    }

    @Override
    public Instant arrivalTime(Instant departure) {
        return departure.plus(transitDuration);
    }

    @Override
    public double cost(CargoDetails cargo) {
        double kg = cargo.weightKg() != null ? cargo.weightKg().doubleValue() : 0;
        return baseCost + costPerKg * kg;
    }

    @Override
    public double co2(CargoDetails cargo) {
        double kg = cargo.weightKg() != null ? cargo.weightKg().doubleValue() : 0;
        return co2PerKg * kg;
    }
}
