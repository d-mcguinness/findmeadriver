package com.driverdirect.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The road HGV/CDL covers-lattice + the null/blank semantics of satisfies. */
class LicenceCategoryTest {

    @Test
    void higherCategoriesCoverLowerOnes() {
        assertThat(LicenceCategory.satisfies("CE", "C")).isTrue();   // C+E covers C
        assertThat(LicenceCategory.satisfies("C", "CE")).isFalse();
        assertThat(LicenceCategory.satisfies("CLASS_A", "CLASS_C")).isTrue();
        assertThat(LicenceCategory.satisfies("CLASS_C", "CLASS_A")).isFalse();
    }

    @Test
    void crossRegimeEquivalence() {
        // UK HGV class 1 ≡ EU C+E (defined in the covers lattice).
        assertThat(LicenceCategory.satisfies("HGV_CLASS_1", "C")).isTrue();
    }

    @Test
    void exactMatchSatisfies() {
        assertThat(LicenceCategory.satisfies("C", "C")).isTrue();
    }

    @Test
    void noRequirementIsAlwaysSatisfied() {
        assertThat(LicenceCategory.satisfies(null, null)).isTrue();
        assertThat(LicenceCategory.satisfies(null, "")).isTrue();
        assertThat(LicenceCategory.satisfies("CLASS_A", null)).isTrue();
    }

    @Test
    void missingLicenceFailsAnyRequirement() {
        assertThat(LicenceCategory.satisfies(null, "C")).isFalse();
        assertThat(LicenceCategory.satisfies("", "C")).isFalse();
    }
}
