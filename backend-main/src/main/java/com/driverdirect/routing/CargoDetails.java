package com.driverdirect.routing;

import java.math.BigDecimal;

/**
 * The shipment quantities a route query prices against — the same shape as
 * the per-mode quantities already carried on {@code Shipment}, minus
 * distanceKm (each edge derives its own distance from its two locations).
 */
public record CargoDetails(
        BigDecimal weightKg,
        BigDecimal volumeM3,
        Integer containerCount,
        Integer pieceCount) {
}
