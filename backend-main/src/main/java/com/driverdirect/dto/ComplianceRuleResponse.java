package com.driverdirect.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Public view of a transport mode's duty/rest ceilings (M5). */
@Data
@AllArgsConstructor
public class ComplianceRuleResponse {
    private String mode;
    private String regulation;
    private double maxDailyHours;
    private double defaultDailyMax;
    private int maxExtendedDaysPerWeek;
    private double maxWeeklyHours;
    private double maxFortnightlyHours;
}
