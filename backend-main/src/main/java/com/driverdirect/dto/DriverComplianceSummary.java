package com.driverdirect.dto;

import lombok.Data;

import java.util.List;

@Data
public class DriverComplianceSummary {
    private List<ComplianceDocumentResponse> documents;
    private boolean allVerified;
    private int verifiedCount;
    private int totalCount;
}
