package com.driverdirect.service;

import com.driverdirect.model.LicenceCategory;
import com.driverdirect.model.Shipment;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Dispatches credential matching by transport mode. The seam M1c opened and M4
 * fills in:
 * <ul>
 *   <li>ROAD — the cross-regime HGV/CDL covers-lattice via {@link LicenceCategory#satisfies}.</li>
 *   <li>AIR / OCEAN / RAIL — the carrier must hold at least one credential tagged
 *       for that mode (e.g. "AIR:ATPL", "OCEAN:STCW", "RAIL:RUL").</li>
 *   <li>INTERMODAL / PARCEL — open (INTERMODAL is never a leg mode; PARCEL has no
 *       credential regime yet).</li>
 * </ul>
 */
@Component
public class CredentialMatcherRegistry {

    /** Road: the existing cross-regime HGV/CDL lattice. */
    private static final CredentialMatcher ROAD =
            (roadLicence, credentials, required) -> LicenceCategory.satisfies(roadLicence, required);

    /** Fallback: no credential regime modelled for this mode. */
    private static final CredentialMatcher OPEN =
            (roadLicence, credentials, required) -> true;

    /** Non-road: carrier must hold ≥1 credential tagged for the mode. */
    private static CredentialMatcher modeMatcher(Shipment.Mode mode) {
        String prefix = mode.name() + ":";
        return (roadLicence, credentials, required) ->
                credentials != null && credentials.stream()
                        .anyMatch(c -> c != null && c.startsWith(prefix));
    }

    private final Map<Shipment.Mode, CredentialMatcher> byMode = new EnumMap<>(Shipment.Mode.class);

    public CredentialMatcherRegistry() {
        byMode.put(Shipment.Mode.ROAD, ROAD);
        byMode.put(Shipment.Mode.AIR, modeMatcher(Shipment.Mode.AIR));
        byMode.put(Shipment.Mode.OCEAN, modeMatcher(Shipment.Mode.OCEAN));
        byMode.put(Shipment.Mode.RAIL, modeMatcher(Shipment.Mode.RAIL));
        // INTERMODAL / PARCEL → OPEN.
    }

    public CredentialMatcher forMode(Shipment.Mode mode) {
        if (mode == null) return ROAD;
        return byMode.getOrDefault(mode, OPEN);
    }

    /** Convenience: dispatch and evaluate in one call. */
    public boolean satisfies(Shipment.Mode mode, String roadLicence, Set<String> credentials, String required) {
        return forMode(mode).satisfies(roadLicence, credentials, required);
    }
}