package com.driverdirect.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class AvailabilityResponse {
    private List<DayAvailability> days;
    private Double weeklyTotal;
    private Double fortnightlyTotal;
    private Double weeklyRemaining;
    private Double fortnightlyRemaining;

    @Data
    public static class DayAvailability {
        private Long id;
        private LocalDate date;
        private Double availableHours;

        public DayAvailability(Long id, LocalDate date, Double availableHours) {
            this.id = id;
            this.date = date;
            this.availableHours = availableHours;
        }
    }
}
