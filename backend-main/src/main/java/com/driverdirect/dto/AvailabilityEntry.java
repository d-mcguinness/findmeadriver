package com.driverdirect.dto;

import com.driverdirect.model.Shipment;
import lombok.Data;

import java.time.LocalDate;

@Data
public class AvailabilityEntry {
    private LocalDate date;
    /** Transport mode this availability is declared for; null = ROAD (back-compat). */
    private Shipment.Mode mode;
    private Double availableHours;
}
