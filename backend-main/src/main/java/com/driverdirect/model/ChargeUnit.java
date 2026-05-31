package com.driverdirect.model;

/**
 * The unit a leg's carrier cost is metered in (M3b). Each transport mode has a
 * natural basis: road by distance, sea/rail by container, air by chargeable
 * weight, parcel by piece. PER_HOUR is the legacy/fallback basis (rate × hours)
 * used when a leg carries no mode-specific quantity.
 */
public enum ChargeUnit {
    PER_KM,
    PER_HOUR,
    PER_CHARGEABLE_KG,
    PER_CONTAINER,
    PER_PIECE,
    FLAT
}
