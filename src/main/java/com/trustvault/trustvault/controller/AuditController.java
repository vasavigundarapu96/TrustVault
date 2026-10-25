
package com.trustvault.trustvault.controller;

import com.trustvault.trustvault.entity.AuditLog;
import com.trustvault.trustvault.service.AuditService;
import com.trustvault.trustvault.repository.AuditLogRepository;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/audit")
public class AuditController {

    private final AuditService auditService;
    private final AuditLogRepository auditLogRepository;

    public AuditController(
            AuditService auditService,
            AuditLogRepository auditLogRepository) {
        this.auditService = auditService;
        this.auditLogRepository = auditLogRepository;
    }

    // Display all audit records
    @GetMapping
    public String showAuditTrail(Model model) {
        List<AuditLog> auditLogs = auditService.getAllLogs();
        model.addAttribute("auditLogs", auditLogs);

        return "audit";
    }

    // Display details of one audit record
    @GetMapping("/{id}")
    public String showAuditDetails(
            @PathVariable Long id,
            Model model) {

        AuditLog auditLog = auditLogRepository.findById(id)
                .orElse(null);

        if (auditLog == null) {
            return "redirect:/audit?notFound";
        }

        model.addAttribute("auditLog", auditLog);

        return "audit-details";
    }
}
