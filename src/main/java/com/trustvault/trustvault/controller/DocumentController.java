
package com.trustvault.trustvault.controller;

import com.trustvault.trustvault.entity.Document;
import com.trustvault.trustvault.entity.PermissionType;
import com.trustvault.trustvault.service.DocumentService;
import com.trustvault.trustvault.service.PermissionService;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;

@Controller
@RequestMapping("/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final PermissionService permissionService;

    public DocumentController(
            DocumentService documentService,
            PermissionService permissionService) {

        this.documentService = documentService;
        this.permissionService = permissionService;
    }

    // Display documents owned by the logged-in user.
    @GetMapping
    public String listDocuments(
            Authentication authentication,
            @RequestParam(required = false) String keyword,
            Model model) {

        String email = authentication.getName();

        List<Document> documents;

        if (keyword == null || keyword.isBlank()) {
            documents = documentService.getMyDocuments(email);
        } else {
            documents =
                    documentService.searchMyDocuments(email, keyword);
        }

        model.addAttribute("documents", documents);

        model.addAttribute(
                "ownedDocumentIds",
                documentService.getOwnedDocumentIds(email));

        model.addAttribute(
                "keyword",
                keyword == null ? "" : keyword);

        return "documents";
    }

    // Display documents shared with the logged-in user.
    @GetMapping("/shared")
    public String listSharedDocuments(
            Authentication authentication,
            @RequestParam(required = false) String keyword,
            Model model) {

        String email = authentication.getName();

        List<Document> documents;

        if (keyword == null || keyword.isBlank()) {
            documents = documentService.getSharedDocuments(email);
        } else {
            documents =
                    documentService.searchSharedDocuments(email, keyword);
        }

        model.addAttribute("documents", documents);

        model.addAttribute(
                "keyword",
                keyword == null ? "" : keyword);

        return "shared-documents";
    }

    // Upload a document.
    @PostMapping("/upload")
    public String uploadDocument(
            Authentication authentication,
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam("file") MultipartFile file,
            RedirectAttributes redirectAttributes) {

        try {
            documentService.upload(
                    authentication.getName(),
                    title,
                    description,
                    file);

            redirectAttributes.addFlashAttribute(
                    "success",
                    "Document uploaded successfully!");

        } catch (IOException | IllegalArgumentException
                 | SecurityException e) {

            redirectAttributes.addFlashAttribute(
                    "error",
                    e.getMessage());
        }

        return "redirect:/documents";
    }

    // Download a document after checking permissions.
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadDocument(
            Authentication authentication,
            @PathVariable Long id) throws IOException {

        Resource resource =
                documentService.downloadMyDocument(
                        authentication.getName(), id);

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"document-" + id + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    // Delete a document. Ownership is checked in DocumentService.
    @PostMapping("/{id}/delete")
    public String deleteDocument(
            Authentication authentication,
            @PathVariable Long id,
            RedirectAttributes redirectAttributes) {

        try {
            documentService.deleteMyDocument(
                    authentication.getName(), id);

            redirectAttributes.addFlashAttribute(
                    "success",
                    "Document deleted successfully!");

        } catch (IOException | IllegalArgumentException
                 | SecurityException e) {

            redirectAttributes.addFlashAttribute(
                    "error",
                    e.getMessage());
        }

        return "redirect:/documents";
    }

    // Share a document. Ownership is checked in PermissionService.
    @PostMapping("/{id}/share")
    public String shareDocument(
            Authentication authentication,
            @PathVariable Long id,
            @RequestParam String recipientEmail,
            @RequestParam PermissionType permissionType,
            RedirectAttributes redirectAttributes) {

        try {
            permissionService.grantPermission(
                    authentication.getName(),
                    id,
                    recipientEmail,
                    permissionType);

            redirectAttributes.addFlashAttribute(
                    "success",
                    "Document shared successfully with "
                            + recipientEmail);

        } catch (IllegalArgumentException | SecurityException e) {

            redirectAttributes.addFlashAttribute(
                    "error",
                    e.getMessage());
        }

        return "redirect:/documents";
    }
}
