package com.driverdirect.dto;

import com.driverdirect.model.Stop;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Wire shape for a single Stop on a Shipment's ordered route. Bundles the
 * Location inline so consumers don't need a second round-trip.
 */
@Data
public class StopResponse {
    private Long id;
    private int sequence;
    private Stop.StopType type;
    private LocationResponse location;
    private LocalDateTime earliestAt;
    private LocalDateTime latestAt;
    private LocalDateTime actualAt;

    public static StopResponse from(Stop s) {
        StopResponse r = new StopResponse();
        r.setId(s.getId());
        r.setSequence(s.getSequence());
        r.setType(s.getType());
        r.setLocation(s.getLocation() != null ? LocationResponse.from(s.getLocation()) : null);
        r.setEarliestAt(s.getEarliestAt());
        r.setLatestAt(s.getLatestAt());
        r.setActualAt(s.getActualAt());
        return r;
    }
}
