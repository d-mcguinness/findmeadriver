package com.driverdirect.service;

import com.driverdirect.model.Carrier;
import com.driverdirect.model.Shipment;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Per-mode compliance rule-sets (M5). The seam M1c anticipated for moving the
 * hardcoded EU-561 ceilings out of {@link AvailabilityServiceImpl} and letting
 * air / maritime / rail regimes plug in.
 *
 * <p>First cut: each mode carries its duty/rest ceilings as data. A carrier's
 * availability is validated against the rule-set for its mode — a single
 * non-road supported mode uses that mode's rules; road-only or genuinely
 * multi-modal carriers use the EU-561 road baseline. Per-mode duty <em>clocks</em>
 * (separate calendars so a pilot's and a trucker's hours don't share one pool)
 * are the remaining large piece.
 */
@Component
public class ComplianceRuleSetRegistry {

    private final Map<Shipment.Mode, ComplianceRuleSet> byMode = new EnumMap<>(Shipment.Mode.class);

    public ComplianceRuleSetRegistry() {
        // road: EU 561/2006 tachograph — 9h default, 10h twice/wk, 56h/wk, 90h/fortnight
        byMode.put(Shipment.Mode.ROAD, new ComplianceRuleSet(
                "EU 561/2006 tachograph", 10, 9, 2, 56, 90));
        // air: EASA flight-time limitations — ~13h flight duty period, ~60h/7d, ~110h/14d
        byMode.put(Shipment.Mode.AIR, new ComplianceRuleSet(
                "EASA flight-time limitations (FTL)", 13, 13, 7, 60, 110));
        // sea: STCW hours of rest — min 10h rest/24h (≤14h work), min 77h rest/7d (≤91h work)
        byMode.put(Shipment.Mode.OCEAN, new ComplianceRuleSet(
                "STCW hours of rest", 14, 14, 7, 91, 182));
        // rail: EU rail working-time — simplified
        byMode.put(Shipment.Mode.RAIL, new ComplianceRuleSet(
                "EU rail working-time directive", 12, 11, 3, 60, 120));
    }

    public ComplianceRuleSet forMode(Shipment.Mode mode) {
        if (mode == null) return byMode.get(Shipment.Mode.ROAD);
        return byMode.getOrDefault(mode, byMode.get(Shipment.Mode.ROAD));
    }

    /** Rule-set for a carrier's availability: a single non-road supported mode
     *  uses that mode's rules; otherwise the EU-561 road baseline. */
    public ComplianceRuleSet forCarrier(Carrier carrier) {
        Set<Shipment.Mode> modes = carrier == null ? null : carrier.getSupportedModes();
        if (modes != null && modes.size() == 1) {
            Shipment.Mode only = modes.iterator().next();
            if (only != Shipment.Mode.ROAD) return forMode(only);
        }
        return forMode(Shipment.Mode.ROAD);
    }

    /** All advertised rule-sets (ROAD, RAIL, OCEAN, AIR), in enum order. */
    public Map<Shipment.Mode, ComplianceRuleSet> all() {
        return byMode;
    }
}
