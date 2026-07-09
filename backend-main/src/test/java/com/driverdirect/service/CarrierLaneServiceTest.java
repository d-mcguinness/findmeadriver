package com.driverdirect.service;

import com.driverdirect.dto.CarrierLaneRequest;
import com.driverdirect.model.Carrier;
import com.driverdirect.model.CarrierLane;
import com.driverdirect.repository.CarrierLaneRepository;
import com.driverdirect.repository.LocationRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Timetable validation on the lane-add path — in particular which
 * serviceModes may back a scheduled service. INTERMODAL is a derived label
 * and PARCEL has no routing-graph transfer support, so both are rejected;
 * ROAD/RAIL/OCEAN/AIR are accepted.
 */
class CarrierLaneServiceTest {

    private final CarrierLaneRepository repository = mock(CarrierLaneRepository.class);
    private final LocationRepository locationRepository = mock(LocationRepository.class);
    private final CarrierLaneService service = new CarrierLaneService(repository, locationRepository);
    private final Carrier carrier = new Carrier();

    private CarrierLaneRequest timetabled(String serviceMode) {
        CarrierLaneRequest r = new CarrierLaneRequest();
        r.setOriginCountry("IE");
        r.setDestinationCountry("NL");
        r.setServiceMode(serviceMode);
        r.setDepartureDays(List.of("MONDAY", "THURSDAY"));
        r.setDepartureTime(LocalTime.of(8, 0));
        r.setTransitDurationHours(24.0);
        return r;
    }

    private void stubUpsert() {
        when(repository.findByCarrierAndOriginCountryAndDestinationCountry(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(repository.save(any(CarrierLane.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void rejectsParcelServiceMode() {
        assertThatThrownBy(() -> service.add(carrier, timetabled("PARCEL")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ROAD, RAIL, OCEAN or AIR");
    }

    @Test
    void rejectsIntermodalServiceMode() {
        assertThatThrownBy(() -> service.add(carrier, timetabled("INTERMODAL")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ROAD, RAIL, OCEAN or AIR");
    }

    @Test
    void acceptsScheduledModes() {
        stubUpsert();
        for (String mode : new String[]{"ROAD", "RAIL", "OCEAN", "AIR"}) {
            assertThat(service.add(carrier, timetabled(mode))).isNotNull();
        }
    }
}
