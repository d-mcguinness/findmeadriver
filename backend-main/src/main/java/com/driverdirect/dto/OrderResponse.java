package com.driverdirect.dto;

import com.driverdirect.model.TransportOrder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class OrderResponse {
    private Long id;
    private Long shipperId;
    private String shipperName;
    private Long customerId;
    private String customerName;
    private String customerReference;
    private String title;
    private String description;
    private String serviceLevel;
    private LocalDate dateNeeded;
    private String currency;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static OrderResponse from(TransportOrder o) {
        OrderResponse r = new OrderResponse();
        r.setId(o.getId());
        r.setShipperId(o.getShipper().getId());
        r.setShipperName(o.getShipper().getCompanyName());
        r.setCustomerId(o.getCustomer().getId());
        r.setCustomerName(o.getCustomer().getName());
        r.setCustomerReference(o.getCustomerReference());
        r.setTitle(o.getTitle());
        r.setDescription(o.getDescription());
        r.setServiceLevel(o.getServiceLevel() != null ? o.getServiceLevel().name() : null);
        r.setDateNeeded(o.getDateNeeded());
        r.setCurrency(o.getCurrency());
        r.setStatus(o.getStatus() != null ? o.getStatus().name() : null);
        r.setCreatedAt(o.getCreatedAt());
        r.setUpdatedAt(o.getUpdatedAt());
        return r;
    }
}
