package com.driverdirect.routing;

import java.time.Instant;
import java.util.List;

/**
 * One finalized, Pareto-best candidate — reconstructed by walking a winning
 * {@link Label}'s parent chain. {@code legs} is a display/acceptance view;
 * accepting an option still goes through the existing
 * CreateIntermodalLoadRequest-shaped submission, unchanged (README.md,
 * "Integration point").
 */
public record RouteOption(
        List<ServiceEdge> legs,
        double totalCost,
        double totalCo2,
        Instant handoverBy,
        Instant arrival) {
}
