
package com.trustvault.trustvault.repository;

import com.trustvault.trustvault.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuditLogRepository
        extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findAllByOrderByCreatedAtDesc();

    @Query("""
        SELECT a FROM AuditLog a
        WHERE LOWER(a.actorEmail) = LOWER(:email)
          AND (
              LOWER(a.action) LIKE '%upload%'
              OR LOWER(a.action) LIKE '%share%'
          )
        ORDER BY a.createdAt DESC
        """)
    List<AuditLog> findMyUploadAndShareActivities(
            @Param("email") String email);
}
