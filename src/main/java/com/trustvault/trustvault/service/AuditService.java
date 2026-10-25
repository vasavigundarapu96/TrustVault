
package com.trustvault.trustvault.service;

import com.trustvault.trustvault.entity.AuditLog;
import com.trustvault.trustvault.repository.AuditLogRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            Long actorUserId,
            String actorEmail,
            String action,
            String outcome,
            String details) {

        AuditLog log = new AuditLog();

        log.setActorUserId(
                actorUserId != null ? actorUserId : 0L);

        log.setActorEmail(
                actorEmail != null ? actorEmail : "anonymous");

        log.setAction(action);
        log.setOutcome(outcome);
        log.setDetails(details);

        auditLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getAllLogs() {
        return auditLogRepository
                .findAllByOrderByCreatedAtDesc();
    }

    
@Transactional(readOnly = true)
public List<AuditLog> getMyActivities(String email) {
    return auditLogRepository.findMyUploadAndShareActivities(email);
}

    
}
