
package com.trustvault.trustvault.service;

import com.trustvault.trustvault.entity.Document;
import com.trustvault.trustvault.entity.PermissionType;
import com.trustvault.trustvault.entity.User;
import com.trustvault.trustvault.repository.DocumentRepository;
import com.trustvault.trustvault.repository.DocumentPermissionRepository;
import com.trustvault.trustvault.repository.UserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final DocumentPermissionRepository permissionRepository;
    private final AuditService auditService;
    private final Path storagePath;

    public DocumentService(
            DocumentRepository documentRepository,
            UserRepository userRepository,
            DocumentPermissionRepository permissionRepository,
            AuditService auditService,
            @Value("${trustvault.storage.location:./trustvault-files}")
            String storageLocation) {

        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
        this.auditService = auditService;

        this.storagePath = Paths.get(storageLocation)
                .toAbsolutePath()
                .normalize();

        try {
            Files.createDirectories(storagePath);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not create private storage directory", e);
        }
    }

    // Upload a new document.
    @Transactional
    public Document upload(
            String email,
            String title,
            String description,
            MultipartFile file) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "Please select a file to upload");
        }

        if (title == null || title.isBlank() || title.length() > 200) {
            throw new IllegalArgumentException(
                    "Enter a title of up to 200 characters");
        }

        if (description != null && description.length() > 1000) {
            throw new IllegalArgumentException(
                    "Description cannot exceed 1000 characters");
        }

        if (file.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException(
                    "File size must not exceed 10 MB");
        }

        String contentType = file.getContentType();

        if (contentType == null || !List.of(
                "application/pdf",
                "image/png",
                "image/jpeg",
                "text/plain").contains(contentType)) {
            throw new IllegalArgumentException(
                    "Only PDF, PNG, JPEG, and TXT files are allowed");
        }

        User owner = findUser(email);

        String storedName = UUID.randomUUID().toString();
        Path target = storagePath.resolve(storedName).normalize();

        if (!target.startsWith(storagePath)) {
            throw new SecurityException("Invalid storage path");
        }

        Files.copy(file.getInputStream(), target);

        try {
            Document document = new Document();
            document.setTitle(title.trim());
            document.setDescription(description);
            document.setStoredFileName(storedName);
            document.setContentType(contentType);
            document.setOwner(owner);

            Document saved = documentRepository.save(document);

            auditService.record(
                    owner.getId(),
                    owner.getEmail(),
                    "DOCUMENT_UPLOAD",
                    "SUCCESS",
                    "Uploaded document ID: " + saved.getId());

            return saved;

        } catch (RuntimeException e) {
            Files.deleteIfExists(target);
            throw e;
        }
    }

    // Return documents owned by the current user.
    @Transactional(readOnly = true)
    public List<Document> getMyDocuments(String email) {

        User user = findUser(email);

        return documentRepository.findByOwnerId(user.getId());
    }

    // Return documents shared with this user by other users.
    @Transactional(readOnly = true)
    public List<Document> getSharedDocuments(String email) {

        User user = findUser(email);

        List<Document> sharedDocuments =
                documentRepository.findSharedDocumentsWithOwner(
                        user.getId());

        return sharedDocuments.stream()
                .filter(document ->
                        !document.getOwner().getId().equals(user.getId()))
                .toList();
    }

    // Search only the current user's documents.
    @Transactional(readOnly = true)
    public List<Document> searchMyDocuments(
            String email, String keyword) {

        String searchTerm = keyword == null
                ? "" : keyword.trim().toLowerCase();

        return getMyDocuments(email).stream()
                .filter(document ->
                        document.getTitle() != null
                        && document.getTitle().toLowerCase()
                                .contains(searchTerm))
                .toList();
    }

    // Search only documents shared with the current user.
    @Transactional(readOnly = true)
    public List<Document> searchSharedDocuments(
            String email, String keyword) {

        String searchTerm = keyword == null
                ? "" : keyword.trim().toLowerCase();

        return getSharedDocuments(email).stream()
                .filter(document ->
                        document.getTitle() != null
                        && document.getTitle().toLowerCase()
                                .contains(searchTerm))
                .toList();
    }

    // Only the owner can delete a document.
    @Transactional
    public void deleteMyDocument(
            String email, Long documentId) throws IOException {

        User user = findUser(email);
        Document document = findDocument(documentId);

        if (!document.getOwner().getId().equals(user.getId())) {
            auditService.record(
                    user.getId(),
                    user.getEmail(),
                    "DOCUMENT_DELETE",
                    "DENIED",
                    "Attempted to delete document ID: " + documentId);

            throw new SecurityException(
                    "You cannot delete another user's document");
        }

        Path target = storagePath
                .resolve(document.getStoredFileName())
                .normalize();

        if (!target.startsWith(storagePath)) {
            throw new SecurityException("Invalid storage path");
        }

        Files.deleteIfExists(target);
        documentRepository.delete(document);

        auditService.record(
                user.getId(),
                user.getEmail(),
                "DOCUMENT_DELETE",
                "SUCCESS",
                "Deleted document ID: " + documentId);
    }

    // Owner or authorized READ/EDIT recipient can download.
    @Transactional(readOnly = true)
    public Resource downloadMyDocument(
            String email, Long documentId) throws IOException {

        User user = findUser(email);
        Document document = findDocument(documentId);

        boolean isOwner =
                document.getOwner().getId().equals(user.getId());

        boolean hasPermission = isOwner
                || permissionRepository
                    .findByDocumentIdAndUserId(
                            documentId, user.getId())
                    .map(permission ->
                            permission.getPermissionType()
                                    == PermissionType.READ
                            || permission.getPermissionType()
                                    == PermissionType.EDIT)
                    .orElse(false);

        if (!hasPermission) {
            auditService.record(
                    user.getId(),
                    user.getEmail(),
                    "DOCUMENT_DOWNLOAD",
                    "DENIED",
                    "Attempted to download document ID: " + documentId);

            throw new SecurityException(
                    "You do not have permission to download this document");
        }

        Path target = storagePath
                .resolve(document.getStoredFileName())
                .normalize();

        if (!target.startsWith(storagePath)
                || !Files.isRegularFile(target)) {
            throw new IllegalArgumentException(
                    "Document file not found");
        }

        auditService.record(
                user.getId(),
                user.getEmail(),
                "DOCUMENT_DOWNLOAD",
                "SUCCESS",
                "Downloaded document ID: " + documentId);

        return new UrlResource(target.toUri());
    }

    // Return IDs of documents owned by this user.
    @Transactional(readOnly = true)
    public List<Long> getOwnedDocumentIds(String email) {

        User user = findUser(email);

        return documentRepository.findByOwnerId(user.getId())
                .stream()
                .map(Document::getId)
                .toList();
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new IllegalArgumentException("User not found"));
    }

    private Document findDocument(Long documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Document not found"));
    }
}
