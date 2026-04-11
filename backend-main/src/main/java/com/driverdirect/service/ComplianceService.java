package com.driverdirect.service;

import com.driverdirect.dto.ComplianceDocumentResponse;
import com.driverdirect.dto.CreateComplianceDocumentRequest;
import com.driverdirect.dto.DriverComplianceSummary;
import com.driverdirect.model.Driver;

import java.util.List;

public interface ComplianceService {

    ComplianceDocumentResponse addDocument(Driver driver, CreateComplianceDocumentRequest request);

    DriverComplianceSummary getComplianceSummary(Driver driver);

    ComplianceDocumentResponse verifyDocument(Long documentId, String notes);

    void deleteDocument(Long documentId, Driver driver);

    boolean isDriverVerified(Driver driver);

    List<ComplianceDocumentResponse> getPendingDocuments();
}
