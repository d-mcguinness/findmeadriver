package com.driverdirect.routing;

import com.driverdirect.model.Shipment;

import java.time.Duration;
import java.time.Instant;

/**
 * A synthetic road leg between two locations, generated on demand during
 * search rather than stored in the graph — see README.md: "Road stays
 * virtual". One of these is created per candidate location pair within
 * radius, not precomputed for every pair up front. The search constructs it
 * from the snapshot alone: coordinates from {@link LocationNode}, rates from
 * {@link RoutingGraph#roadTariff()} — never from live repositories or the
 * live pricing bean, so a query's ~30 flexible-window searches all price
 * consistently.
 */
public record RoadEdge(
        Long originLocationId,
        Long destinationLocationId,
        double distanceKm,
        double avgSpeedKph,
        Tariff tariff) implements ServiceEdge {

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
        return tariff.cost(cargo, distanceKm);
    }

    /** 0 until EmissionPolicy lands (README build order step 4). */
    @Override
    public double co2(CargoDetails cargo) {
        return 0;
    }
}
