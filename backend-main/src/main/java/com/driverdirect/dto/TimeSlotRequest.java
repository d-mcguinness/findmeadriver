package com.driverdirect.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class TimeSlotRequest {
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
}
