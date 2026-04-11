package com.driverdirect.controller;

import com.driverdirect.dto.ComplianceDocumentResponse;
import com.driverdirect.service.AdminService;
import com.driverdirect.service.ComplianceService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@AllArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class AdminController {

    private final AdminService adminService;
    private final ComplianceService complianceService;

    @GetMapping("/compliance/pending")
    public ResponseEntity<List<ComplianceDocumentResponse>> getPendingDocuments() {
        return ResponseEntity.ok(complianceService.getPendingDocuments());
    }

    @PutMapping("/compliance/{id}/verify")
    public ResponseEntity<ComplianceDocumentResponse> verifyDocument(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String notes = body != null ? body.get("notes") : null;
        return ResponseEntity.ok(complianceService.verifyDocument(id, notes));
    }
}
