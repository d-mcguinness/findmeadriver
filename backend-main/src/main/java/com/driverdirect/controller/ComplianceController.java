package com.driverdirect.controller;

import com.driverdirect.dto.ComplianceRuleResponse;
import com.driverdirect.service.ComplianceRuleSet;
import com.driverdirect.service.ComplianceRuleSetRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public per-mode compliance rules (M5). Unauthenticated reference data
 * (SecurityConfig permits {@code /api/compliance/rules}) so the marketing and
 * carrier-onboarding pages can show the duty/rest limits per mode.
 */
@RestController
@RequestMapping("/api/compliance")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class ComplianceController {

    private final ComplianceRuleSetRegistry ruleSets;

    @GetMapping("/rules")
    public List<ComplianceRuleResponse> rules() {
        return ruleSets.all().entrySet().stream()
                .map(e -> {
                    ComplianceRuleSet rs = e.getValue();
                    return new ComplianceRuleResponse(
                            e.getKey().name(), rs.regulation(), rs.maxDailyHours(), rs.defaultDailyMax(),
                            rs.maxExtendedDaysPerWeek(), rs.maxWeeklyHours(), rs.maxFortnightlyHours());
                })
                .toList();
    }
}
