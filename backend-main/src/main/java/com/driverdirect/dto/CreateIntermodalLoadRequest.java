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
    private String currency;
    private List<CreateLegRequest> legs;
}
