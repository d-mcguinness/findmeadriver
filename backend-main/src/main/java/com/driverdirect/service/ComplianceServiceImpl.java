package com.driverdirect.service;

import com.driverdirect.dto.ComplianceDocumentResponse;
import com.driverdirect.dto.CreateComplianceDocumentRequest;
import com.driverdirect.dto.DriverComplianceSummary;
import com.driverdirect.model.ComplianceDocument;
import com.driverdirect.model.DocumentStatus;
import com.driverdirect.model.Driver;
import com.driverdirect.repository.ComplianceDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ComplianceServiceImpl implements ComplianceService {

    private final ComplianceDocumentRepository documentRepository;

    @Override
    @Transactional
    public ComplianceDocumentResponse addDocument(Driver driver, CreateComplianceDocumentRequest request) {
        // Replace existing document of same type
        documentRepository.findByDriverAndDocumentType(driver, request.getDocumentType())
                .ifPresent(existing -> documentRepository.delete(existing));

        ComplianceDocument doc = new ComplianceDocument();
        doc.setDriver(driver);
        doc.setDocumentType(request.getDocumentType());
        doc.setDocumentNumber(request.getDocumentNumber());
        doc.setExpiryDate(request.getExpiryDate());
        doc.setStatus(DocumentStatus.PENDING);

        doc = documentRepository.save(doc);
        return ComplianceDocumentResponse.from(doc);
    }

    @Override
    public DriverComplianceSummary getComplianceSummary(Driver driver) {
        List<ComplianceDocument> docs = documentRepository.findByDriverOrderByUploadedAtDesc(driver);

        DriverComplianceSummary summary = new DriverComplianceSummary();
        summary.setDocuments(docs.stream().map(ComplianceDocumentResponse::from).collect(Collectors.toList()));
        summary.setTotalCount(docs.size());

        int verified = (int) docs.stream().filter(d -> d.getStatus() == DocumentStatus.VERIFIED).count();
        summary.setVerifiedCount(verified);
        summary.setAllVerified(!docs.isEmpty() && verified == docs.size());

        return summary;
    }

    @Override
    @Transactional
    public ComplianceDocumentResponse verifyDocument(Long documentId, String notes) {
        ComplianceDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        doc.setStatus(DocumentStatus.VERIFIED);
        doc.setVerifiedAt(LocalDateTime.now());
        doc.setNotes(notes);
        doc = documentRepository.save(doc);
        return ComplianceDocumentResponse.from(doc);
    }

    @Override
    @Transactional
    public void deleteDocument(Long documentId, Driver driver) {
        ComplianceDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        if (!doc.getDriver().getId().equals(driver.getId())) {
            throw new IllegalArgumentException("You can only delete your own documents");
        }

        documentRepository.delete(doc);
    }

    @Override
    public boolean isDriverVerified(Driver driver) {
        List<ComplianceDocument> docs = documentRepository.findByDriverOrderByUploadedAtDesc(driver);
        if (docs.isEmpty()) return false;
        return docs.stream().allMatch(d -> d.getStatus() == DocumentStatus.VERIFIED);
    }

    @Override
    public List<ComplianceDocumentResponse> getPendingDocuments() {
        return documentRepository.findByStatus(DocumentStatus.PENDING).stream()
                .map(ComplianceDocumentResponse::from)
                .collect(Collectors.toList());
    }
}
