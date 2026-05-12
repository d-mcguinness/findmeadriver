package com.driverdirect.repository;

import com.driverdirect.model.OrderItem;
import com.driverdirect.model.TransportOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrder(TransportOrder order);
}
