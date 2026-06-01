package com.driverdirect.service;

import com.driverdirect.dto.ComplianceDocumentResponse;
import com.driverdirect.dto.CreateComplianceDocumentRequest;
import com.driverdirect.dto.CarrierComplianceSummary;
import com.driverdirect.model.ComplianceDocument;
import com.driverdirect.model.DocumentStatus;
import com.driverdirect.model.Carrier;
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
    public ComplianceDocumentResponse addDocument(Carrier carrier, CreateComplianceDocumentRequest request) {
        // Replace existing document of same type
        documentRepository.findByCarrierAndDocumentType(carrier, request.getDocumentType())
                .ifPresent(existing -> documentRepository.delete(existing));

        ComplianceDocument doc = new ComplianceDocument();
        doc.setCarrier(carrier);
        doc.setDocumentType(request.getDocumentType());
        doc.setDocumentNumber(request.getDocumentNumber());
        doc.setExpiryDate(request.getExpiryDate());
        doc.setStatus(DocumentStatus.PENDING);

        doc = documentRepository.save(doc);
        return ComplianceDocumentResponse.from(doc);
    }

    @Override
    public CarrierComplianceSummary getComplianceSummary(Carrier carrier) {
        List<ComplianceDocument> docs = documentRepository.findByCarrierOrderByUploadedAtDesc(carrier);

        CarrierComplianceSummary summary = new CarrierComplianceSummary();
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
    public void deleteDocument(Long documentId, Carrier carrier) {
        ComplianceDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        if (!doc.getCarrier().getId().equals(carrier.getId())) {
            throw new IllegalArgumentException("You can only delete your own documents");
        }

        documentRepository.delete(doc);
    }

    @Override
    public boolean isCarrierVerified(Carrier carrier) {
        List<ComplianceDocument> docs = documentRepository.findByCarrierOrderByUploadedAtDesc(carrier);
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
