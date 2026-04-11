package com.driverdirect.dto;

import com.driverdirect.model.DocumentType;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateComplianceDocumentRequest {
    private DocumentType documentType;
    private String documentNumber;
    private LocalDate expiryDate;
}
