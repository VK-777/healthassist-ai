package com.healthassist.service;

import com.healthassist.model.AuditLog;
import com.healthassist.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void logStage(String workflowId, String stage, String details) {
        AuditLog auditLog = new AuditLog(workflowId, stage, LocalDateTime.now(), details);
        auditLogRepository.save(auditLog);
        log.debug("Audit [{}] Stage: {} — {}", workflowId, stage, details);
    }

    public List<AuditLog> getAuditTrail(String workflowId) {
        return auditLogRepository.findByWorkflowIdOrderByTimestampAsc(workflowId);
    }
}
