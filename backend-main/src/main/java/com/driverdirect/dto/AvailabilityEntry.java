package com.driverdirect.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class AvailabilityEntry {
    private LocalDate date;
    private Double availableHours;
}
