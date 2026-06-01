package com.driverdirect.repository;

import com.driverdirect.model.Carrier;
import com.driverdirect.model.CarrierTimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface CarrierTimeSlotRepository extends JpaRepository<CarrierTimeSlot, Long> {
    List<CarrierTimeSlot> findByCarrierAndDateBetweenOrderByDateAscStartTimeAsc(
            Carrier carrier, LocalDate start, LocalDate end);

    List<CarrierTimeSlot> findByCarrierAndDateOrderByStartTimeAsc(Carrier carrier, LocalDate date);
}