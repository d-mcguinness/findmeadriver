package com.driverdirect.util;

/**
 * Single source of truth for ISO-3166 alpha-2 country-code handling. Lanes,
 * cabotage operations, shipments, and locations must all store the same
 * canonical (uppercase) form, otherwise case-sensitive comparisons — e.g. the
 * lane filter's {@code Objects.equals(loadCountry, laneCountry)} — silently miss.
 */
public final class CountryCodes {

    private CountryCodes() {}

    /**
     * Canonical storage form: trimmed + uppercased. Null/blank → null. Use on
     * write paths where the value is optional (load/shipment/stop countries).
     */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase();
    }

    /**
     * Validate a required alpha-2 code, returning its canonical form or throwing
     * IllegalArgumentException. Use on user-facing inputs that must be present
     * (lane endpoints, home-country).
     */
    public static String require(String raw, String field) {
        if (raw == null || raw.trim().length() != 2) {
            throw new IllegalArgumentException(field + " must be a 2-letter ISO code");
        }
        return raw.trim().toUpperCase();
    }
}
