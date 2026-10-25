
package com.trustvault.trustvault.controller;

import com.trustvault.trustvault.entity.AuditLog;
import com.trustvault.trustvault.service.AuditService;
import com.trustvault.trustvault.service.DocumentService;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class DashboardController {

    private final DocumentService documentService;
    private final AuditService auditService;

    public DashboardController(
            DocumentService documentService,
            AuditService auditService) {
        this.documentService = documentService;
        this.auditService = auditService;
    }

    @GetMapping("/dashboard")
    public String dashboard(
            Authentication authentication,
            Model model) {

        String email = authentication.getName();

        model.addAttribute(
                "documentCount",
                documentService.getMyDocuments(email).size());

        model.addAttribute(
                "sharedDocumentCount",
                documentService.getSharedDocuments(email).size());

        List<AuditLog> activities =
                auditService.getMyActivities(email);

        model.addAttribute("activities", activities);

        return "dashboard";
    }
}
