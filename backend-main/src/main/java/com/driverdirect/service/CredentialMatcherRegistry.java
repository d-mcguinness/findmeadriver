package com.driverdirect.service;

import com.driverdirect.model.LicenceCategory;
import com.driverdirect.model.Shipment;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * Dispatches credential matching by transport mode (M1c). This is the seam that
 * M4's air-crew / maritime / rail credential models plug into — register a
 * {@link CredentialMatcher} per mode here.
 *
 * <p>For now only ROAD has a real matcher (the HGV/CDL covers-lattice via
 * {@link LicenceCategory#satisfies}). Non-road modes match permissively: the
 * road licensing regime simply does not apply to them, so air/sea/rail loads are
 * no longer wrongly blocked at browse/apply time.
 */
@Component
public class CredentialMatcherRegistry {

    /** Road: the existing cross-regime HGV/CDL lattice. */
    private static final CredentialMatcher ROAD = LicenceCategory::satisfies;

    /** Non-road (M1c): no road-licence requirement yet. */
    private static final CredentialMatcher OPEN = (have, required) -> true;

    private final Map<Shipment.Mode, CredentialMatcher> byMode = new EnumMap<>(Shipment.Mode.class);

    public CredentialMatcherRegistry() {
        byMode.put(Shipment.Mode.ROAD, ROAD);
        // RAIL / OCEAN / AIR / INTERMODAL / PARCEL fall through to OPEN until M4.
    }

    public CredentialMatcher forMode(Shipment.Mode mode) {
        if (mode == null) return ROAD;
        return byMode.getOrDefault(mode, OPEN);
    }

    /** Convenience: dispatch and evaluate in one call. */
    public boolean satisfies(Shipment.Mode mode, String have, String required) {
        return forMode(mode).satisfies(have, required);
    }
}