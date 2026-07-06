package com.driverdirect.routing;

import com.driverdirect.model.Shipment;

import java.math.BigDecimal;

/**
 * The shipment quantities a route query prices against — the same shape as
 * the per-mode quantities already carried on {@code Shipment}, minus
 * distanceKm (each edge derives its own distance from its two locations).
 *
 * <p>This is the routing side of a deliberately duplicated shape:
 * {@code Shipment} (and the create-request DTOs) declare the same fields.
 * All mapping between the two worlds goes through {@link #from(Shipment)}
 * so the shapes can't silently drift — if a quantity is added on one side,
 * this factory is the single place that must change.
 */
public record CargoDetails(
        BigDecimal weightKg,
        BigDecimal volumeM3,
        Integer containerCount,
        Integer pieceCount) {

    public static CargoDetails from(Shipment s) {
        return new CargoDetails(
                s.getWeightKg(), s.getVolumeM3(), s.getContainerCount(), s.getPieceCount());
    }
}
