package com.driverdirect.dto;

import com.driverdirect.model.Shipment;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class AvailabilityResponse {
    private List<DayAvailability> days;
    /** Per-mode duty clocks (declared / committed / remaining over week + fortnight). */
    private List<DutyClock> dutyClocks;
    // Cross-mode declared totals (kept for the legacy summary line). Per-mode
    // headroom now lives in dutyClocks; these aggregate remainings are null.
    private Double weeklyTotal;
    private Double fortnightlyTotal;
    private Double weeklyRemaining;
    private Double fortnightlyRemaining;

    @Data
    public static class DayAvailability {
        private Long id;
        private LocalDate date;
        private Shipment.Mode mode;
        private Double availableHours;

        public DayAvailability(Long id, LocalDate date, Shipment.Mode mode, Double availableHours) {
            this.id = id;
            this.date = date;
            this.mode = mode;
            this.availableHours = availableHours;
        }
    }
}
