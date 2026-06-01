package com.driverdirect.service;

import com.driverdirect.dto.ComplianceDocumentResponse;
import com.driverdirect.dto.CreateComplianceDocumentRequest;
import com.driverdirect.dto.CarrierComplianceSummary;
import com.driverdirect.model.Carrier;

import java.util.List;

public interface ComplianceService {

    ComplianceDocumentResponse addDocument(Carrier carrier, CreateComplianceDocumentRequest request);

    CarrierComplianceSummary getComplianceSummary(Carrier carrier);

    ComplianceDocumentResponse verifyDocument(Long documentId, String notes);

    void deleteDocument(Long documentId, Carrier carrier);

    boolean isCarrierVerified(Carrier carrier);

    List<ComplianceDocumentResponse> getPendingDocuments();
}
