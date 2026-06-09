package com.huawei.it.roma.liveeda.policycenter.service;

import com.huawei.it.roma.liveeda.policycenter.api.ApiException;
import com.huawei.it.roma.liveeda.policycenter.api.ErrorCode;
import com.huawei.it.roma.liveeda.policycenter.audit.AuditLogger;
import com.huawei.it.roma.liveeda.policycenter.audit.NoopAuditLogger;
import com.huawei.it.roma.liveeda.policycenter.domain.AuthMode;
import com.huawei.it.roma.liveeda.policycenter.domain.ToolPolicy;
import com.huawei.it.roma.liveeda.policycenter.repository.ToolPolicyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ToolPolicyService {

    private final ToolPolicyRepository repository;
    private final Clock clock;
    private final AuditLogger auditLogger;

    public ToolPolicyService(ToolPolicyRepository repository) {
        this(repository, Clock.systemUTC(), NoopAuditLogger.INSTANCE);
    }

    @Autowired
    public ToolPolicyService(ToolPolicyRepository repository, AuditLogger auditLogger) {
        this(repository, Clock.systemUTC(), auditLogger);
    }

    ToolPolicyService(ToolPolicyRepository repository, Clock clock, AuditLogger auditLogger) {
        this.repository = repository;
        this.clock = clock;
        this.auditLogger = auditLogger;
    }

    public List<ToolPolicy> listPolicies(String agentId) {
        try {
            return repository.findByAgentId(agentId);
        } catch (RuntimeException exception) {
            throw new ApiException(ErrorCode.POLICY_STORE_UNAVAILABLE, "policy store is unavailable");
        }
    }

    public ToolPolicySaveResult replacePolicies(String agentId, List<ToolPolicyUpdate> updates) {
        if (updates == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "tools must not be null");
        }

        List<ToolPolicy> policies = new ArrayList<>();
        Set<String> seenToolIds = new HashSet<>();
        for (ToolPolicyUpdate update : updates) {
            if (update == null || update.toolId() == null || update.toolId().isBlank()) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "toolId must not be blank");
            }
            if (!seenToolIds.add(update.toolId())) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "duplicate toolId: " + update.toolId());
            }
            policies.add(new ToolPolicy(agentId, update.toolId(), AuthMode.normalize(update.authMode())));
        }

        try {
            repository.replaceAll(agentId, policies);
        } catch (RuntimeException exception) {
            throw new ApiException(ErrorCode.POLICY_STORE_UNAVAILABLE, "policy store is unavailable");
        }
        auditReplacement(agentId, policies.size());
        return new ToolPolicySaveResult(agentId, policies.size(), Instant.now(clock));
    }

    private void auditReplacement(String agentId, int toolCount) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("eventType", "TOOL_POLICY_REPLACED");
        fields.put("agentId", agentId);
        fields.put("toolCount", Integer.toString(toolCount));
        auditLogger.record("TOOL_POLICY_REPLACED", fields);
    }
}
