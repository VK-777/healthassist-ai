package com.healthassist.controller;

import com.healthassist.model.AuditLog;
import com.healthassist.service.AuditService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/{workflowId}")
    public ResponseEntity<Map<String, Object>> getAuditTrail(@PathVariable String workflowId) {
        List<AuditLog> logs = auditService.getAuditTrail(workflowId);

        if (logs.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<Map<String, Object>> stages = logs.stream()
                .map(log -> {
                    Map<String, Object> stage = new LinkedHashMap<>();
                    stage.put("stage", log.getStage());
                    stage.put("timestamp", log.getTimestamp().toString());
                    stage.put("details", log.getDetails());
                    return stage;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("workflowId", workflowId);
        response.put("stages", stages);

        return ResponseEntity.ok(response);
    }
}
