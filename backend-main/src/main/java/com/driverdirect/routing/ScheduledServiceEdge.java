package com.driverdirect.routing;

import com.driverdirect.model.Shipment;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * A rail/sea/air service backed by a real carrier lane's timetable.
 * {@code departures} is a placeholder shape for this skeleton — the real
 * version attaches to {@code CarrierLane} once it grows timetable columns
 * (README.md build order step 2); nothing here touches that entity yet.
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
