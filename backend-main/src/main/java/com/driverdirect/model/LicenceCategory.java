package com.driverdirect.model;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Typed licence categories for the EU, UK, and US driving regimes. Stored as
 * the enum {@code name()} on Carrier.licenceCategory and Load.requiredLicenceCategory
 * (both still typed as String for forward-compatibility with regimes we haven't
 * modelled yet — e.g. AU, CA).
 *
 * <p>Categories form a "covers" lattice: a carrier holding category X can apply
 * to a load requiring category Y when {@code X.covers(Y)} — i.e. X grants at
 * least the entitlement of Y, possibly under a different national naming
 * (UK HGV class 1 ≡ EU C+E).
 *
 * <p>Note: Java enum names disallow '+', so EU "C+E" is stored as {@code CE}
 * and displayed via {@link #getDisplayName()}.
 */
public enum LicenceCategory {

    // ---- EU categories (Directive 2006/126/EC) ----
    C("EU", "C", "Rigid HGV > 3.5 t"),
    C1("EU", "C1", "Rigid HGV 3.5–7.5 t"),
    CE("EU", "C+E", "Articulated HGV (C + trailer)"),
    C1E("EU", "C1+E", "C1 + trailer"),
    D("EU", "D", "Bus, 9+ seats"),
    D1("EU", "D1", "Minibus 9–16 seats"),
    DE("EU", "D+E", "D + trailer"),
    D1E("EU", "D1+E", "D1 + trailer"),

    // ---- UK legacy aliases (still printed on older paper-counterpart licences) ----
    HGV_CLASS_1("UK", "HGV Class 1", "UK class 1 — equivalent to EU C+E"),
    HGV_CLASS_2("UK", "HGV Class 2", "UK class 2 — equivalent to EU C"),

    // ---- US CDL ----
    CLASS_A("US", "CDL Class A", "Combination > 26,001 lbs"),
    CLASS_B("US", "CDL Class B", "Single vehicle > 26,001 lbs"),
    CLASS_C("US", "CDL Class C", "Smaller vehicles, hazmat or passenger"),
    NON_CDL("US", "Non-CDL", "No commercial licence required");

    private final String region;
    private final String displayName;
    private final String description;

    LicenceCategory(String region, String displayName, String description) {
        this.region = region;
        this.displayName = displayName;
        this.description = description;
    }

    public String getRegion() { return region; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }

    // Categories this entitlement also satisfies. Populated below so the lattice
    // can reference enum constants without forward-reference grief.
    private static final Map<LicenceCategory, Set<LicenceCategory>> COVERS =
            new EnumMap<>(LicenceCategory.class);

    static {
        // EU cargo hierarchy: CE > C > C1; C1E sits between C1 and CE.
        COVERS.put(CE,  EnumSet.of(C, C1, C1E, HGV_CLASS_1, HGV_CLASS_2));
        COVERS.put(C,   EnumSet.of(C1, HGV_CLASS_2));
        COVERS.put(C1E, EnumSet.of(C1));
        COVERS.put(C1,  EnumSet.noneOf(LicenceCategory.class));

        // EU passenger hierarchy.
        COVERS.put(DE,  EnumSet.of(D, D1, D1E));
        COVERS.put(D,   EnumSet.of(D1));
        COVERS.put(D1E, EnumSet.of(D1));
        COVERS.put(D1,  EnumSet.noneOf(LicenceCategory.class));

        // UK ↔ EU equivalence (two-way: a CE carrier can take an HGV-Class-1 load
        // and vice versa).
        COVERS.put(HGV_CLASS_1, EnumSet.of(CE, C, C1, C1E, HGV_CLASS_2));
        COVERS.put(HGV_CLASS_2, EnumSet.of(C, C1));

        // US CDL hierarchy.
        COVERS.put(CLASS_A, EnumSet.of(CLASS_B, CLASS_C, NON_CDL));
        COVERS.put(CLASS_B, EnumSet.of(CLASS_C, NON_CDL));
        COVERS.put(CLASS_C, EnumSet.of(NON_CDL));
        COVERS.put(NON_CDL, EnumSet.noneOf(LicenceCategory.class));
    }

    /** True when this entitlement grants at least what {@code required} grants. */
    public boolean covers(LicenceCategory required) {
        if (required == null) return true;
        if (this == required) return true;
        Set<LicenceCategory> set = COVERS.get(this);
        return set != null && set.contains(required);
    }

    /**
     * String-form check used by service code that still stores categories as
     * {@code String}. Unknown values fall back to plain equality so legacy data
     * (or future regimes we haven't added) keeps working.
     */
    public static boolean satisfies(String haveName, String requiredName) {
        if (requiredName == null || requiredName.isBlank()) return true;
        if (haveName == null || haveName.isBlank()) return false;
        LicenceCategory have = tryParse(haveName);
        LicenceCategory required = tryParse(requiredName);
        if (have == null || required == null) {
            return haveName.equals(requiredName);
        }
        return have.covers(required);
    }

    private static LicenceCategory tryParse(String name) {
        try {
            return LicenceCategory.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
