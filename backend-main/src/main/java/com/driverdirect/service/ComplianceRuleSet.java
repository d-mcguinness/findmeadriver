package com.driverdirect.service;

/**
 * Per-mode duty/rest ceilings (M5). The shape mirrors what
 * {@link AvailabilityServiceImpl} enforces on a carrier's availability calendar;
 * the values differ by regulatory regime (road tachograph vs air FTL vs maritime
 * watch-keeping). Where {@code defaultDailyMax == maxDailyHours} the "extended
 * day" rule is dormant (modes without that concept).
 */
public record ComplianceRuleSet(
        String regulation,
        double maxDailyHours,
        double defaultDailyMax,
        int maxExtendedDaysPerWeek,
        double maxWeeklyHours,
        double maxFortnightlyHours) {
}
