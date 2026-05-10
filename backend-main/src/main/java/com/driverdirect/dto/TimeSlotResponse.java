package com.driverdirect.dto;

import com.driverdirect.model.DriverTimeSlot;
import lombok.Data;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class TimeSlotResponse {
    private Long id;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private double hours;

    public static TimeSlotResponse from(DriverTimeSlot s) {
        TimeSlotResponse r = new TimeSlotResponse();
        r.setId(s.getId());
        r.setDate(s.getDate());
        r.setStartTime(s.getStartTime());
        r.setEndTime(s.getEndTime());
        r.setHours(Duration.between(s.getStartTime(), s.getEndTime()).toMinutes() / 60.0);
        return r;
    }
}
