package com.driverdirect.routing;

import com.driverdirect.model.Shipment;

import java.time.Duration;
import java.time.Instant;

/**
 * A synthetic road leg between two locations, generated on demand during
 * search rather than stored in the graph — see README.md: "Road stays
 * virtual". One of these is created per candidate location pair within
 * radius, not precomputed for every pair up front.
 */
public record RoadEdge(
        Long originLocationId,
        Long destinationLocationId,
        double distanceKm,
        double avgSpeedKph,
        double baseCost,
        double costPerKm,
        double co2PerKm) implements ServiceEdge {

    @Override
    public Shipment.Mode mode() {
        return Shipment.Mode.ROAD;
    }

    @Override
    public Instant nextDeparture(Instant after) {
        return after; // no schedule — always available
    }

    @Override
    public Instant arrivalTime(Instant departure) {
        return departure.plus(Duration.ofMinutes((long) (distanceKm / avgSpeedKph * 60)));
    }

    @Override
    public double cost(CargoDetails cargo) {
        return baseCost + costPerKm * distanceKm;
    }

    @Override
    public double co2(CargoDetails cargo) {
        return co2PerKm * distanceKm;
    }
}
