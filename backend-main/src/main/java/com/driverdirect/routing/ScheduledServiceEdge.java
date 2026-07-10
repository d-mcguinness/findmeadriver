package com.driverdirect.routing;

import com.driverdirect.model.Shipment;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * A rail/sea/air service compiled from a timetabled
 * {@link com.driverdirect.model.CarrierLane}. Holds the recurring weekly
 * <em>pattern</em> — days + origin-local time + zone — not a materialised
 * departure list: {@link #nextDeparture} resolves each occurrence on the fly
 * (at most 8 candidate days), so the graph never decays with the passage of
 * time, far-future queries work without a planning horizon, and DST is
 * handled per occurrence rather than baked in wrong at build time.
 *
 * <p>Gap/overlap semantics on DST transition days follow java.time: a
 * departure time inside the spring-forward gap shifts forward by the gap
 * length; a time repeated at the autumn overlap takes the earlier offset.
 * ScheduledServiceEdgeTest pins both.
 */
public record ScheduledServiceEdge(
        Long originLocationId,
        Long destinationLocationId,
        Shipment.Mode mode,
        Set<DayOfWeek> departureDays,
        LocalTime departureTime,
        ZoneId zone,
        Duration transitDuration,
        double distanceKm,
        LegRates rates) implements ServiceEdge {

    public ScheduledServiceEdge {
        departureDays = departureDays.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(departureDays));
    }

    @Override
    public Instant nextDeparture(Instant after) {
        if (departureDays.isEmpty()) return null;
        LocalDate from = after.atZone(zone).toLocalDate();
        for (int i = 0; i <= 7; i++) {
            LocalDate day = from.plusDays(i);
            if (!departureDays.contains(day.getDayOfWeek())) continue;
            Instant departure = ZonedDateTime.of(day, departureTime, zone).toInstant();
            if (!departure.isBefore(after)) return departure;
        }
        return null; // unreachable with a non-empty day set, but be safe
    }

    @Override
    public Instant arrivalTime(Instant departure) {
        return departure.plus(transitDuration);
    }

    @Override
    public double cost(CargoDetails cargo) {
        return rates.cost(cargo, distanceKm);
    }

    @Override
    public double co2(CargoDetails cargo) {
        return rates.co2(cargo, distanceKm);
    }
}
