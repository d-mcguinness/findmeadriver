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
 * <p>{@code handoverBy} is the departure instant of this plan's first leg —
 * the option's actual handover time. The design's "latest viable handover"
 * (holding cargo in a free warehouse until the last moment that still
 * catches the plan) is step-5 flexible-window work, not computed here.
 */
public record RouteOption(
        List<ServiceEdge> legs,
        double totalCost,
        double totalCo2,
        Instant handoverBy,
        Instant arrival) {
}
