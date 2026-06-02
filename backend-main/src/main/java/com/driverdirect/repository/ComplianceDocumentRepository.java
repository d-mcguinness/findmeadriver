package com.driverdirect.repository;

import com.driverdirect.model.ComplianceDocument;
import com.driverdirect.model.DocumentStatus;
import com.driverdirect.model.DocumentType;
import com.driverdirect.model.Carrier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ComplianceDocumentRepository extends JpaRepository<ComplianceDocument, Long> {

    List<ComplianceDocument> findByCarrierOrderByUploadedAtDesc(Carrier carrier);

    List<ComplianceDocument> findByCarrierAndStatus(Carrier carrier, DocumentStatus status);

    List<ComplianceDocument> findByStatus(DocumentStatus status);

    Optional<ComplianceDocument> findByCarrierAndDocumentType(Carrier carrier, DocumentType type);

    long countByCarrierAndStatus(Carrier carrier, DocumentStatus status);
}
