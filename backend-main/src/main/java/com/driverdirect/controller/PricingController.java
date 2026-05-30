package com.driverdirect.controller;

import com.driverdirect.dto.ModePricingResponse;
import com.driverdirect.service.PricingPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public pricing information for the marketing site. Unauthenticated (see
 * SecurityConfig: {@code /api/pricing/**} is permitAll) so the landing and
 * pricing pages can render per-mode commission rates without a login.
 */
@RestController
@RequestMapping("/api/pricing")
@RequiredArgsConstructor
public class PricingController {

    private final PricingPolicy pricingPolicy;

    /** Per-mode commission rates + pricing basis for the modes we advertise. */
    @GetMapping("/modes")
    public List<ModePricingResponse> modes() {
        return ModePricingResponse.marketed(pricingPolicy);
    }
}