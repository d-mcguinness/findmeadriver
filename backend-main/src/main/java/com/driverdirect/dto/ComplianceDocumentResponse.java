package com.driverdirect.dto;

import com.driverdirect.model.ComplianceDocument;
import com.driverdirect.model.DocumentStatus;
import com.driverdirect.model.DocumentType;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ComplianceDocumentResponse {
    private Long id;
    private DocumentType documentType;
    private String documentNumber;
    private LocalDate expiryDate;
    private DocumentStatus status;
    private LocalDateTime uploadedAt;
    private LocalDateTime verifiedAt;
    private String notes;

    public static ComplianceDocumentResponse from(ComplianceDocument doc) {
        ComplianceDocumentResponse r = new ComplianceDocumentResponse();
        r.setId(doc.getId());
        r.setDocumentType(doc.getDocumentType());
        r.setDocumentNumber(doc.getDocumentNumber());
        r.setExpiryDate(doc.getExpiryDate());
        r.setStatus(doc.getStatus());
        r.setUploadedAt(doc.getUploadedAt());
        r.setVerifiedAt(doc.getVerifiedAt());
        r.setNotes(doc.getNotes());
        return r;
    }
}
