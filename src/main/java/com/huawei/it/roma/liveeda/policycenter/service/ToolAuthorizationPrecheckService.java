package com.huawei.it.roma.liveeda.policycenter.service;

import com.huawei.it.roma.liveeda.policycenter.api.ApiException;
import com.huawei.it.roma.liveeda.policycenter.api.ErrorCode;
import com.huawei.it.roma.liveeda.policycenter.audit.AuditLogger;
import com.huawei.it.roma.liveeda.policycenter.audit.NoopAuditLogger;
import com.huawei.it.roma.liveeda.policycenter.domain.AgentPolicyTool;
import com.huawei.it.roma.liveeda.policycenter.domain.AuthorizationDecision;
import com.huawei.it.roma.liveeda.policycenter.domain.Decision;
import com.huawei.it.roma.liveeda.policycenter.domain.DecisionReason;
import com.huawei.it.roma.liveeda.policycenter.domain.InvalidTokenIdException;
import com.huawei.it.roma.liveeda.policycenter.domain.TokenId;
import com.huawei.it.roma.liveeda.policycenter.repository.AgentPolicyToolCatalogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ToolAuthorizationPrecheckService {

    private static final int MAX_PRECHECK_TOOL_COUNT = 100;
    private static final int MAX_SERVER_ID_LENGTH = 128;
    private static final int MAX_TOOL_NAME_LENGTH = 255;

    private final AgentPolicyToolCatalogRepository catalogRepository;
    private final AuthorizationDecisionService decisionService;
    private final AuditLogger auditLogger;

    public ToolAuthorizationPrecheckService(
            AgentPolicyToolCatalogRepository catalogRepository,
            AuthorizationDecisionService decisionService) {
        this(catalogRepository, decisionService, NoopAuditLogger.INSTANCE);
    }

    @Autowired
    public ToolAuthorizationPrecheckService(
            AgentPolicyToolCatalogRepository catalogRepository,
            AuthorizationDecisionService decisionService,
            AuditLogger auditLogger) {
        this.catalogRepository = catalogRepository;
        this.decisionService = decisionService;
        this.auditLogger = auditLogger;
    }

    public ToolAuthorizationPrecheckResult precheck(String tokenIdRaw, List<ToolAuthorizationPrecheckTool> tools) {
        TokenId tokenId = parseForRequest(tokenIdRaw);
        if (tools == null || tools.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "tools must not be empty");
        }
        if (tools.size() > MAX_PRECHECK_TOOL_COUNT) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "tools size must not exceed 100");
        }

        List<ToolAuthorizationRequiredTool> requiredTools = new ArrayList<>();
        for (ToolAuthorizationPrecheckTool tool : tools) {
            validateTool(tool);
            AgentPolicyTool catalogTool = findCatalogTool(tokenId, tool);
            AuthorizationDecision decision = decisionService.precheck(tokenId.raw(), catalogTool.toolId());
            if (decision.reason() == DecisionReason.POLICY_STORE_UNAVAILABLE) {
                throw new ApiException(ErrorCode.POLICY_STORE_UNAVAILABLE, "policy store is unavailable");
            }
            if (decision.reason() == DecisionReason.AUTHORIZATION_STORE_UNAVAILABLE) {
                throw new ApiException(ErrorCode.AUTHORIZATION_STORE_UNAVAILABLE, "authorization store is unavailable");
            }
            if (decision.decision() == Decision.AUTHORIZATION_REQUIRED) {
                requiredTools.add(new ToolAuthorizationRequiredTool(
                        catalogTool.serviceId(),
                        catalogTool.effectiveServerName(),
                        catalogTool.toolName(),
                        catalogTool.toolId(),
                        decision.decision()));
            }
        }

        audit(tokenId, tools.size(), requiredTools.size());
        return new ToolAuthorizationPrecheckResult(tokenId.raw(), List.copyOf(requiredTools));
    }

    private AgentPolicyTool findCatalogTool(TokenId tokenId, ToolAuthorizationPrecheckTool tool) {
        try {
            return catalogRepository.findBoundTool(tokenId.agentId(), tool.serverId(), tool.toolName())
                    .orElseThrow(() -> new ApiException(
                            ErrorCode.INVALID_REQUEST,
                            "tool is not found in agent policy tool catalog"));
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ApiException(
                    ErrorCode.POLICY_STORE_UNAVAILABLE,
                    "policy store is unavailable",
                    exception,
                    context("FIND_AGENT_POLICY_TOOL", tokenId, tool));
        }
    }

    private void validateTool(ToolAuthorizationPrecheckTool tool) {
        if (tool == null || tool.serverId() == null || tool.serverId().isBlank()
                || tool.toolName() == null || tool.toolName().isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "serverId and toolName must not be blank");
        }
        if (tool.serverId().length() > MAX_SERVER_ID_LENGTH) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "serverId length must not exceed 128");
        }
        if (tool.toolName().length() > MAX_TOOL_NAME_LENGTH) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "toolName length must not exceed 255");
        }
    }

    private TokenId parseForRequest(String tokenIdRaw) {
        try {
            return TokenId.parse(tokenIdRaw);
        } catch (InvalidTokenIdException exception) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, exception.getMessage());
        }
    }

    private Map<String, String> context(String operation, TokenId tokenId, ToolAuthorizationPrecheckTool tool) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("operation", operation);
        fields.put("tokenId", tokenId.raw());
        fields.put("agentId", tokenId.agentId());
        fields.put("userId", tokenId.userId());
        fields.put("conversationId", tokenId.conversationId());
        fields.put("serverId", tool.serverId());
        fields.put("toolName", tool.toolName());
        return fields;
    }

    private void audit(TokenId tokenId, int toolCount, int authorizationRequiredCount) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("eventType", "TOOL_AUTHORIZATION_PRECHECKED");
        fields.put("tokenId", tokenId.raw());
        fields.put("agentId", tokenId.agentId());
        fields.put("userId", tokenId.userId());
        fields.put("conversationId", tokenId.conversationId());
        fields.put("toolCount", Integer.toString(toolCount));
        fields.put("authorizationRequiredCount", Integer.toString(authorizationRequiredCount));
        auditLogger.record("TOOL_AUTHORIZATION_PRECHECKED", fields);
    }
}
