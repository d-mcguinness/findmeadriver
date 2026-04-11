package com.driverdirect.dto;

import lombok.Data;

import java.util.List;

@Data
public class WeeklyAvailabilityRequest {
    private List<AvailabilityEntry> entries;
}
