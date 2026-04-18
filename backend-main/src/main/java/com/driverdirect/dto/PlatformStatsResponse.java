package com.driverdirect.dto;

import lombok.Data;

@Data
public class PlatformStatsResponse {
    private long totalUsers;
    private long totalDrivers;
    private long totalEmployers;
    private long totalJobs;
    private long openJobs;
    private long assignedJobs;
    private long inProgressJobs;
    private long completedJobs;
    private long cancelledJobs;
    private long pendingDocuments;
}
