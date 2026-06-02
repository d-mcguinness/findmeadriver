package com.driverdirect.repository;

import com.driverdirect.model.Carrier;
import com.driverdirect.model.CarrierLane;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CarrierLaneRepository extends JpaRepository<CarrierLane, Long> {

    List<CarrierLane> findByCarrierOrderByOriginCountryAscDestinationCountryAsc(Carrier carrier);

    Optional<CarrierLane> findByCarrierAndOriginCountryAndDestinationCountry(
            Carrier carrier, String originCountry, String destinationCountry);

    long countByCarrier(Carrier carrier);
}
