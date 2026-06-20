package com.driverdirect.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * International-vs-domestic leg classification ({@link MovementType}) — the
 * load/leg side of cabotage. Same country = DOMESTIC (a foreign carrier doing
 * this is cabotage); different countries = INTERNATIONAL (a cabotage-granting
 * entry); missing metadata = UNKNOWN (strict — never silently domestic).
 * Also covers the delegation chain Load → Shipment.
 */
class MovementTypeTest {

    // ---- pure classification --------------------------------------------------

    @Test
    void sameCountryIsDomestic() {
        assertThat(MovementType.of("FR", "FR")).isEqualTo(MovementType.DOMESTIC);
    }

    @Test
    void differentCountriesIsInternational() {
        assertThat(MovementType.of("PL", "FR")).isEqualTo(MovementType.INTERNATIONAL);
    }

    @Test
    void comparisonIsCaseInsensitive() {
        assertThat(MovementType.of("fr", "FR")).isEqualTo(MovementType.DOMESTIC);
        assertThat(MovementType.of("pl", "FR")).isEqualTo(MovementType.INTERNATIONAL);
    }

    @Test
    void missingOrBlankCountryIsUnknown() {
        assertThat(MovementType.of(null, "FR")).isEqualTo(MovementType.UNKNOWN);
        assertThat(MovementType.of("FR", null)).isEqualTo(MovementType.UNKNOWN);
        assertThat(MovementType.of(null, null)).isEqualTo(MovementType.UNKNOWN);
        assertThat(MovementType.of("", "FR")).isEqualTo(MovementType.UNKNOWN);
        assertThat(MovementType.of("FR", "  ")).isEqualTo(MovementType.UNKNOWN);
    }

    // ---- delegation: Shipment and Load read off the leg's countries -----------

    @Test
    void shipmentDerivesFromItsCountries() {
        Shipment s = new Shipment();
        s.setOriginCountry("DE");
        s.setDestinationCountry("DE");
        assertThat(s.getMovementType()).isEqualTo(MovementType.DOMESTIC);
    }

    @Test
    void loadDelegatesToItsShipment() {
        Shipment s = new Shipment();
        s.setOriginCountry("PL");
        s.setDestinationCountry("FR");
        Load l = new Load();
        l.setShipment(s);
        assertThat(l.getMovementType()).isEqualTo(MovementType.INTERNATIONAL);
    }

    @Test
    void loadWithNoShipmentIsUnknown() {
        assertThat(new Load().getMovementType()).isEqualTo(MovementType.UNKNOWN);
    }
}