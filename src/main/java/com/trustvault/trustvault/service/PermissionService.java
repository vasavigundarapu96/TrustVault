
package com.trustvault.trustvault.service;

import com.trustvault.trustvault.entity.Document;
import com.trustvault.trustvault.entity.DocumentPermission;
import com.trustvault.trustvault.entity.PermissionType;
import com.trustvault.trustvault.entity.User;
import com.trustvault.trustvault.repository.DocumentPermissionRepository;
import com.trustvault.trustvault.repository.DocumentRepository;
import com.trustvault.trustvault.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PermissionService {

    private final DocumentPermissionRepository permissionRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public PermissionService(
            DocumentPermissionRepository permissionRepository,
            DocumentRepository documentRepository,
            UserRepository userRepository,
            AuditService auditService) {

        this.permissionRepository = permissionRepository;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public DocumentPermission grantPermission(
            String ownerEmail,
            Long documentId,
            String recipientEmail,
            PermissionType permissionType) {

        User owner = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() ->
                        new IllegalArgumentException("Owner not found"));

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Document not found"));

        if (!document.getOwner().getId().equals(owner.getId())) {
            auditService.record(
                    owner.getId(), owner.getEmail(),
                    "GRANT_PERMISSION", "DENIED",
                    "Document ID: " + documentId);

            throw new SecurityException(
                    "Only the document owner can grant permission");
        }

        User recipient = userRepository.findByEmail(recipientEmail)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Recipient account not found"));

        if (recipient.getId().equals(owner.getId())) {
            throw new IllegalArgumentException(
                    "You already own this document");
        }

        if (permissionType == null) {
            throw new IllegalArgumentException(
                    "Select READ or EDIT permission");
        }

        DocumentPermission permission = permissionRepository
                .findByDocumentIdAndUserId(
                        documentId, recipient.getId())
                .orElseGet(DocumentPermission::new);

        permission.setDocument(document);
        permission.setUser(recipient);
        permission.setPermissionType(permissionType);

        DocumentPermission saved =
                permissionRepository.save(permission);

        auditService.record(
                owner.getId(), owner.getEmail(),
                "GRANT_PERMISSION", "SUCCESS",
                "Document ID: " + documentId
                        + ", recipient: " + recipientEmail
                        + ", permission: " + permissionType);

        return saved;
    }

    @Transactional
    public void revokePermission(
            String ownerEmail,
            Long documentId,
            Long recipientUserId) {

        User owner = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() ->
                        new IllegalArgumentException("Owner not found"));

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Document not found"));

        if (!document.getOwner().getId().equals(owner.getId())) {
            auditService.record(
                    owner.getId(), owner.getEmail(),
                    "REVOKE_PERMISSION", "DENIED",
                    "Document ID: " + documentId);

            throw new SecurityException(
                    "Only the owner can revoke permission");
        }

        DocumentPermission permission = permissionRepository
                .findByDocumentIdAndUserId(
                        documentId, recipientUserId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Permission does not exist"));

        permissionRepository.delete(permission);

        auditService.record(
                owner.getId(), owner.getEmail(),
                "REVOKE_PERMISSION", "SUCCESS",
                "Document ID: " + documentId
                        + ", recipient ID: " + recipientUserId);
    }

    @Transactional(readOnly = true)
    public List<DocumentPermission> getPermissions(
            String ownerEmail, Long documentId) {

        User owner = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() ->
                        new IllegalArgumentException("Owner not found"));

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Document not found"));

        if (!document.getOwner().getId().equals(owner.getId())) {
            throw new SecurityException(
                    "Only the owner can view this permission list");
        }

        return permissionRepository.findByDocumentId(documentId);
    }

    @Transactional(readOnly = true)
    public boolean canRead(Long documentId, Long userId) {

        Document document = documentRepository.findById(documentId)
                .orElse(null);

        if (document == null) {
            return false;
        }

        if (document.getOwner().getId().equals(userId)) {
            return true;
        }

        return permissionRepository
                .findByDocumentIdAndUserId(documentId, userId)
                .isPresent();
    }

    @Transactional(readOnly = true)
    public boolean canEdit(Long documentId, Long userId) {

        Document document = documentRepository.findById(documentId)
                .orElse(null);

        if (document == null) {
            return false;
        }

        if (document.getOwner().getId().equals(userId)) {
            return true;
        }

        return permissionRepository
                .findByDocumentIdAndUserId(documentId, userId)
                .map(p -> p.getPermissionType() == PermissionType.EDIT)
                .orElse(false);
    }
}
