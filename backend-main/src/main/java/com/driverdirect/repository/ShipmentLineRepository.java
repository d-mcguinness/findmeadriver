package com.driverdirect.repository;

import com.driverdirect.model.Shipment;
import com.driverdirect.model.ShipmentLine;
import com.driverdirect.model.TransportOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShipmentLineRepository extends JpaRepository<ShipmentLine, Long> {
    List<ShipmentLine> findByShipment(Shipment shipment);
    List<ShipmentLine> findByOrder(TransportOrder order);
}