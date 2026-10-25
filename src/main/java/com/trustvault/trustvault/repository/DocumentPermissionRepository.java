
package com.trustvault.trustvault.repository;

import com.trustvault.trustvault.entity.DocumentPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DocumentPermissionRepository
        extends JpaRepository<DocumentPermission, Long> {

    List<DocumentPermission> findByUserId(Long userId);

    List<DocumentPermission> findByDocumentId(Long documentId);

    Optional<DocumentPermission> findByDocumentIdAndUserId(
        Long documentId, Long userId
    );

    void deleteByDocumentIdAndUserId(
        Long documentId, Long userId
    );
}
