package com.driverdirect.dto;

import com.driverdirect.model.Driver;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CreateJobRequest {
    private String title;
    private String description;
    private String pickupLocation;
    private String deliveryLocation;
    private Double estimatedDurationHours;
    private LocalDate dateNeeded;
    private BigDecimal ratePerHour;
    private Driver.CDLType requiredCdlType;
}
