package com.huawei.it.roma.liveeda.policycenter.api.controller;

import com.huawei.it.roma.liveeda.policycenter.api.ApiException;
import com.huawei.it.roma.liveeda.policycenter.api.ErrorCode;
import com.huawei.it.roma.liveeda.policycenter.api.dto.SaveToolPoliciesRequest;
import com.huawei.it.roma.liveeda.policycenter.api.dto.ToolPolicyItemResponse;
import com.huawei.it.roma.liveeda.policycenter.api.dto.ToolPolicyListResponse;
import com.huawei.it.roma.liveeda.policycenter.domain.ToolPolicy;
import com.huawei.it.roma.liveeda.policycenter.service.ToolPolicySaveResult;
import com.huawei.it.roma.liveeda.policycenter.service.ToolPolicyService;
import com.huawei.it.roma.liveeda.policycenter.service.ToolPolicyUpdate;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@RestController
public class AdminToolPolicyController {

    private final ToolPolicyService service;

    public AdminToolPolicyController(ToolPolicyService service) {
        this.service = service;
    }

    @GetMapping("/admin/agents/{agentId}/tool-policies")
    ToolPolicyListResponse list(@PathVariable String agentId) {
        ensureNotBlank(agentId, "agentId must not be blank");
        List<ToolPolicy> policies = service.listPolicies(agentId);
        List<ToolPolicyItemResponse> tools = policies.stream()
                .map(policy -> new ToolPolicyItemResponse(
                        policy.toolId(),
                        policy.effectiveAuthMode(),
                        policy.updatedAt()))
                .toList();
        Instant updatedAt = policies.stream()
                .map(ToolPolicy::updatedAt)
                .filter(value -> value != null)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return new ToolPolicyListResponse(agentId, tools, updatedAt);
    }

    @PutMapping("/admin/agents/{agentId}/tool-policies")
    ToolPolicySaveResult replace(
            @PathVariable String agentId,
            @Valid @RequestBody SaveToolPoliciesRequest request) {
        ensureNotBlank(agentId, "agentId must not be blank");
        List<ToolPolicyUpdate> updates = request.tools().stream()
                .map(tool -> new ToolPolicyUpdate(tool.toolId(), tool.authMode()))
                .toList();
        return service.replacePolicies(agentId, updates);
    }

    private void ensureNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, message);
        }
    }
}
