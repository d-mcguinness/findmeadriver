package com.driverdirect.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * One transport mode's duty clock for a carrier over the current week + fortnight:
 * how much they've DECLARED available, how much is already COMMITTED to assigned
 * loads, and how much REMAINS (bounded by the mode's own regulatory ceiling).
 * remaining = max(0, min(ceiling, declared) − committed).
 */
@Data
@AllArgsConstructor
public class DutyClock {
    private String mode;
    private String regulation;
    private double maxDailyHours;
    private double maxWeeklyHours;
    private double maxFortnightlyHours;

    private double declaredThisWeek;
    private double committedThisWeek;
    private double remainingThisWeek;

    private double declaredFortnight;
    private double committedFortnight;
    private double remainingFortnight;
}
