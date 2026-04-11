package com.driverdirect.repository;

import com.driverdirect.model.ComplianceDocument;
import com.driverdirect.model.DocumentStatus;
import com.driverdirect.model.DocumentType;
import com.driverdirect.model.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ComplianceDocumentRepository extends JpaRepository<ComplianceDocument, Long> {

    List<ComplianceDocument> findByDriverOrderByUploadedAtDesc(Driver driver);

    List<ComplianceDocument> findByDriverAndStatus(Driver driver, DocumentStatus status);

    List<ComplianceDocument> findByStatus(DocumentStatus status);

    Optional<ComplianceDocument> findByDriverAndDocumentType(Driver driver, DocumentType type);

    long countByDriverAndStatus(Driver driver, DocumentStatus status);
}
