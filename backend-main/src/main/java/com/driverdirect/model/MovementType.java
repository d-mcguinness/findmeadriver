package com.driverdirect.model;

/**
 * International-vs-domestic classification of a transport leg, derived from its
 * origin and destination countries.
 *
 * <p>This is the load/leg-level half of the cabotage picture. Cabotage itself is
 * (a {@link #DOMESTIC} leg) carried by a foreign-based carrier — the carrier side
 * is decided in {@link com.driverdirect.service.CabotageService}; this enum only
 * classifies the movement. {@link #INTERNATIONAL} legs are the inbound deliveries
 * that, under EC 1072/2009, grant cabotage rights.
 *
 * <p>Strict by design: when either country is missing we return {@link #UNKNOWN}
 * rather than silently assuming domestic or international. Callers should treat
 * UNKNOWN as needs-review, not as "safe".
 */
public enum MovementType {
    /** Origin and destination are the same country. A foreign carrier doing this is cabotage. */
    DOMESTIC,
    /** Origin and destination differ — a cross-border movement (a cabotage-granting entry). */
    INTERNATIONAL,
    /** Country metadata is missing on one or both ends — cannot be classified. */
    UNKNOWN;

    /**
     * Classify a leg from its two ISO country codes. Comparison is
     * case-insensitive; a null or blank on either side yields {@link #UNKNOWN}.
     */
    public static MovementType of(String originCountry, String destinationCountry) {
        if (originCountry == null || originCountry.isBlank()
                || destinationCountry == null || destinationCountry.isBlank()) {
            return UNKNOWN;
        }
        return originCountry.equalsIgnoreCase(destinationCountry) ? DOMESTIC : INTERNATIONAL;
    }
}