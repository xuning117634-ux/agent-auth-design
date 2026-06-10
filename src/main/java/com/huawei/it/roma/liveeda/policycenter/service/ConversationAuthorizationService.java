package com.huawei.it.roma.liveeda.policycenter.service;

import com.huawei.it.roma.liveeda.policycenter.api.ApiException;
import com.huawei.it.roma.liveeda.policycenter.api.ErrorCode;
import com.huawei.it.roma.liveeda.policycenter.audit.AuditLogger;
import com.huawei.it.roma.liveeda.policycenter.audit.NoopAuditLogger;
import com.huawei.it.roma.liveeda.policycenter.domain.AuthMode;
import com.huawei.it.roma.liveeda.policycenter.domain.AuthorizationStatus;
import com.huawei.it.roma.liveeda.policycenter.domain.InvalidTokenIdException;
import com.huawei.it.roma.liveeda.policycenter.domain.TokenId;
import com.huawei.it.roma.liveeda.policycenter.domain.ToolPolicy;
import com.huawei.it.roma.liveeda.policycenter.repository.ToolPolicyRepository;
import com.huawei.it.roma.liveeda.policycenter.store.ConversationAuthorizationStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class ConversationAuthorizationService {

    private final ToolPolicyRepository policyRepository;
    private final ConversationAuthorizationStore authorizationStore;
    private final Duration authorizationTtl;
    private final AuditLogger auditLogger;

    public ConversationAuthorizationService(
            ToolPolicyRepository policyRepository,
            ConversationAuthorizationStore authorizationStore,
            @Value("${policy-center.authorization.ttl:7d}") Duration authorizationTtl) {
        this(policyRepository, authorizationStore, authorizationTtl, NoopAuditLogger.INSTANCE);
    }

    @Autowired
    public ConversationAuthorizationService(
            ToolPolicyRepository policyRepository,
            ConversationAuthorizationStore authorizationStore,
            @Value("${policy-center.authorization.ttl:7d}") Duration authorizationTtl,
            AuditLogger auditLogger) {
        this.policyRepository = policyRepository;
        this.authorizationStore = authorizationStore;
        this.authorizationTtl = authorizationTtl;
        this.auditLogger = auditLogger;
    }

    public ConversationAuthorizationResult authorize(String tokenIdRaw, String toolId) {
        TokenId tokenId = parseForRequest(tokenIdRaw);
        ToolPolicy policy = findPolicy(tokenId.agentId(), toolId)
                .orElseThrow(() -> new ApiException(ErrorCode.TOOL_NOT_BOUND, "tool is not bound"));

        if (policy.effectiveAuthMode() == AuthMode.NO_AUTH_REQUIRED) {
            throw new ApiException(ErrorCode.AUTHORIZATION_NOT_REQUIRED, "authorization is not required");
        }

        try {
            authorizationStore.authorize(tokenId.raw(), toolId, authorizationTtl);
        } catch (RuntimeException exception) {
            throw new ApiException(ErrorCode.AUTHORIZATION_STORE_UNAVAILABLE, "authorization store is unavailable");
        }
        audit("CONVERSATION_AUTHORIZED", tokenId, toolId, Map.of("status", AuthorizationStatus.AUTHORIZED.name()));
        return new ConversationAuthorizationResult(AuthorizationStatus.AUTHORIZED, tokenId.raw(), toolId);
    }

    public AuthorizationStatus status(String tokenIdRaw, String toolId) {
        TokenId tokenId = parseForRequest(tokenIdRaw);
        try {
            AuthorizationStatus status = authorizationStore.exists(tokenId.raw(), toolId)
                    ? AuthorizationStatus.AUTHORIZED
                    : AuthorizationStatus.NOT_AUTHORIZED;
            audit("AUTHORIZATION_STATUS_QUERIED", tokenId, toolId, Map.of("status", status.name()));
            return status;
        } catch (RuntimeException exception) {
            throw new ApiException(ErrorCode.AUTHORIZATION_STORE_UNAVAILABLE, "authorization store is unavailable");
        }
    }

    public CleanupResult cleanup(String tokenIdRaw) {
        TokenId tokenId = parseForRequest(tokenIdRaw);
        return cleanup(tokenId);
    }

    public CleanupResult cleanup(String agentId, String userId, String conversationId) {
        TokenId tokenId = tokenIdForRequest(agentId, userId, conversationId);
        return cleanup(tokenId);
    }

    private CleanupResult cleanup(TokenId tokenId) {
        try {
            long deleted = authorizationStore.cleanup(tokenId.raw());
            audit("CONVERSATION_AUTHORIZATION_CLEANED", tokenId, null, Map.of("deletedGrantCount", Long.toString(deleted)));
            return new CleanupResult("CLEARED", deleted);
        } catch (RuntimeException exception) {
            throw new ApiException(ErrorCode.AUTHORIZATION_STORE_UNAVAILABLE, "authorization store is unavailable");
        }
    }

    private Optional<ToolPolicy> findPolicy(String agentId, String toolId) {
        try {
            return policyRepository.findByAgentIdAndToolId(agentId, toolId);
        } catch (RuntimeException exception) {
            throw new ApiException(ErrorCode.POLICY_STORE_UNAVAILABLE, "policy store is unavailable");
        }
    }

    private TokenId tokenIdForRequest(String agentId, String userId, String conversationId) {
        try {
            return TokenId.of(agentId, userId, conversationId);
        } catch (InvalidTokenIdException exception) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, exception.getMessage());
        }
    }

    private TokenId parseForRequest(String tokenIdRaw) {
        try {
            return TokenId.parse(tokenIdRaw);
        } catch (InvalidTokenIdException exception) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, exception.getMessage());
        }
    }

    private void audit(String eventType, TokenId tokenId, String toolId, Map<String, String> extraFields) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("eventType", eventType);
        fields.put("tokenId", tokenId.raw());
        fields.put("agentId", tokenId.agentId());
        fields.put("userId", tokenId.userId());
        fields.put("conversationId", tokenId.conversationId());
        if (toolId != null) {
            fields.put("toolId", toolId);
        }
        fields.putAll(extraFields);
        auditLogger.record(eventType, fields);
    }
}
