
package com.trustvault.trustvault.repository;

import com.trustvault.trustvault.entity.Document;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository
        extends JpaRepository<Document, Long> {

    // Find documents owned by a user.
    List<Document> findByOwnerId(Long ownerId);

    // Find documents by title.
    List<Document> findByTitleContainingIgnoreCase(String title);

    // Find shared documents and load their owners at the same time.
    @Query("""
        SELECT DISTINCT d
        FROM Document d
        JOIN FETCH d.owner
        JOIN d.permissions p
        WHERE p.user.id = :userId
        """)
    List<Document> findSharedDocumentsWithOwner(
            @Param("userId") Long userId);
}
