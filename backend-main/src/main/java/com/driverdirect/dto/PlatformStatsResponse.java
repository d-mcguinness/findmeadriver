package com.driverdirect.dto;

import lombok.Data;

@Data
public class PlatformStatsResponse {
    private long totalUsers;
    private long totalCarriers;
    private long totalShippers;
    private long totalLoads;
    private long openLoads;
    private long assignedLoads;
    private long inProgressLoads;
    private long completedLoads;
    private long cancelledLoads;
    private long pendingDocuments;
}
