package com.driverdirect.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * Request to post a true intermodal movement (M2b): order-level metadata plus
 * an ordered list of legs, each with its own mode, route, and carrier rate.
 */
@Data
public class CreateIntermodalLoadRequest {
    private String title;
    private String description;
    private LocalDate dateNeeded;
    // Optional flexible-window context from a routing search (see README.md,
    // "Proposed: multimodal routing engine"); dateNeeded above stays the
    // authoritative single date regardless — these are richer context only.
    private LocalDate earliestReadyDate;
    private LocalDate latestHandoverDate;
    private LocalDate arrivalDeadline;
    private String currency;
    private List<CreateLegRequest> legs;
}
