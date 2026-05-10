package com.driverdirect.repository;

import com.driverdirect.model.Driver;
import com.driverdirect.model.DriverTimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DriverTimeSlotRepository extends JpaRepository<DriverTimeSlot, Long> {
    List<DriverTimeSlot> findByDriverAndDateBetweenOrderByDateAscStartTimeAsc(
            Driver driver, LocalDate start, LocalDate end);

    List<DriverTimeSlot> findByDriverAndDateOrderByStartTimeAsc(Driver driver, LocalDate date);
}