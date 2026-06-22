package com.huawei.it.roma.liveeda.policycenter.api.controller;

import com.huawei.it.roma.liveeda.policycenter.api.ApiException;
import com.huawei.it.roma.liveeda.policycenter.api.ErrorCode;
import com.huawei.it.roma.liveeda.policycenter.api.dto.AccessibleAgentItemResponse;
import com.huawei.it.roma.liveeda.policycenter.api.dto.AccessibleAgentListResponse;
import com.huawei.it.roma.liveeda.policycenter.api.dto.AccessibleToolItemResponse;
import com.huawei.it.roma.liveeda.policycenter.api.dto.AccessibleToolListResponse;
import com.huawei.it.roma.liveeda.policycenter.api.dto.AgentAccessDecisionRequest;
import com.huawei.it.roma.liveeda.policycenter.api.dto.AgentAccessDecisionResponse;
import com.huawei.it.roma.liveeda.policycenter.domain.AgentAccessDecision;
import com.huawei.it.roma.liveeda.policycenter.service.AccessibleToolView;
import com.huawei.it.roma.liveeda.policycenter.service.UserPolicyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class UserPolicyQueryController {

    private final UserPolicyService service;

    public UserPolicyQueryController(UserPolicyService service) {
        this.service = service;
    }

    @PostMapping("/internal/agent-access-decisions")
    AgentAccessDecisionResponse decideAgentAccess(@Valid @RequestBody AgentAccessDecisionRequest request) {
        AgentAccessDecision decision = service.decideAgentAccess(request.agentId(), request.userId());
        return new AgentAccessDecisionResponse(
                decision.agentId(),
                decision.userId(),
                decision.allowed(),
                decision.reason());
    }

    @GetMapping("/internal/users/{userId}/agents")
    AccessibleAgentListResponse listAccessibleAgents(@PathVariable String userId) {
        ensureNotBlank(userId, "userId must not be blank");
        List<AccessibleAgentItemResponse> agents = service.listAccessibleAgents(userId).stream()
                .map(decision -> new AccessibleAgentItemResponse(decision.agentId(), decision.reason()))
                .toList();
        return new AccessibleAgentListResponse(userId, agents);
    }

    @GetMapping("/internal/agents/{agentId}/users/{userId}/tools")
    AccessibleToolListResponse listAccessibleTools(
            @PathVariable String agentId,
            @PathVariable String userId) {
        ensureNotBlank(agentId, "agentId must not be blank");
        ensureNotBlank(userId, "userId must not be blank");
        List<AccessibleToolItemResponse> tools = service.listAccessibleTools(agentId, userId).stream()
                .map(this::toToolResponse)
                .toList();
        return new AccessibleToolListResponse(agentId, userId, tools);
    }

    private AccessibleToolItemResponse toToolResponse(AccessibleToolView tool) {
        return new AccessibleToolItemResponse(tool.serverName(), tool.toolName(), tool.toolId(), tool.authMode());
    }

    private void ensureNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, message);
        }
    }
}
