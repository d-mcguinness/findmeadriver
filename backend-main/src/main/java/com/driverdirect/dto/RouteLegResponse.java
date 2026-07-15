package com.driverdirect.dto;

import com.driverdirect.routing.ScheduledServiceEdge;
import com.driverdirect.routing.ServiceEdge;
import lombok.Data;

import java.util.Map;

/**
 * One leg of a proposed route: a single-mode movement between two graph
 * nodes. {@code scheduled} distinguishes a timetabled carrier service (rail/
 * sea/air) from a virtual road leg. Location names are resolved from the
 * snapshot the option was planned against.
 */
@Data
public class RouteLegResponse {

    private String mode;
    private Long originLocationId;
    private String originLocationName;
    private Long destinationLocationId;
    private String destinationLocationName;
    private boolean scheduled;

    public static RouteLegResponse from(ServiceEdge edge, Map<Long, String> locationNames) {
        RouteLegResponse r = new RouteLegResponse();
        r.setMode(edge.mode() != null ? edge.mode().name() : null);
        r.setOriginLocationId(edge.originLocationId());
        r.setOriginLocationName(locationNames.get(edge.originLocationId()));
        r.setDestinationLocationId(edge.destinationLocationId());
        r.setDestinationLocationName(locationNames.get(edge.destinationLocationId()));
        r.setScheduled(edge instanceof ScheduledServiceEdge);
        return r;
    }
}
