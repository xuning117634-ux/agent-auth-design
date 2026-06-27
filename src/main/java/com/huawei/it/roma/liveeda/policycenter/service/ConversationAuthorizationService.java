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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ConversationAuthorizationService {

    private static final int MAX_BATCH_TOOL_COUNT = 100;
    private static final int MAX_TOOL_ID_LENGTH = 255;

    private final ToolPolicyRepository policyRepository;
    private final ConversationAuthorizationStore authorizationStore;
    private final Duration authorizationTtl;
    private final Duration maxAuthorizationTtl;
    private final AuditLogger auditLogger;

    public ConversationAuthorizationService(
            ToolPolicyRepository policyRepository,
            ConversationAuthorizationStore authorizationStore,
            @Value("${policy-center.authorization.ttl:7d}") Duration authorizationTtl) {
        this(policyRepository, authorizationStore, authorizationTtl, Duration.ofDays(30), NoopAuditLogger.INSTANCE);
    }

    public ConversationAuthorizationService(
            ToolPolicyRepository policyRepository,
            ConversationAuthorizationStore authorizationStore,
            Duration authorizationTtl,
            Duration maxAuthorizationTtl) {
        this(policyRepository, authorizationStore, authorizationTtl, maxAuthorizationTtl, NoopAuditLogger.INSTANCE);
    }

    public ConversationAuthorizationService(
            ToolPolicyRepository policyRepository,
            ConversationAuthorizationStore authorizationStore,
            Duration authorizationTtl,
            AuditLogger auditLogger) {
        this(policyRepository, authorizationStore, authorizationTtl, Duration.ofDays(30), auditLogger);
    }

    @Autowired
    public ConversationAuthorizationService(
            ToolPolicyRepository policyRepository,
            ConversationAuthorizationStore authorizationStore,
            @Value("${policy-center.authorization.ttl:7d}") Duration authorizationTtl,
            @Value("${policy-center.authorization.max-ttl:30d}") Duration maxAuthorizationTtl,
            AuditLogger auditLogger) {
        this.policyRepository = policyRepository;
        this.authorizationStore = authorizationStore;
        this.authorizationTtl = authorizationTtl;
        this.maxAuthorizationTtl = maxAuthorizationTtl;
        this.auditLogger = auditLogger;
    }

    public ConversationAuthorizationResult authorize(String tokenIdRaw, String toolId) {
        return authorize(tokenIdRaw, toolId, null);
    }

    public ConversationAuthorizationResult authorize(String tokenIdRaw, String toolId, Long expiresInSeconds) {
        TokenId tokenId = parseForRequest(tokenIdRaw);
        Duration ttl = effectiveTtl(expiresInSeconds);
        ToolPolicy policy = findPolicy(tokenId, toolId)
                .orElseThrow(() -> new ApiException(ErrorCode.TOOL_NOT_BOUND, "tool is not bound"));

        if (policy.effectiveAuthMode() == AuthMode.NO_AUTH_REQUIRED) {
            throw new ApiException(ErrorCode.AUTHORIZATION_NOT_REQUIRED, "authorization is not required");
        }

        try {
            authorizationStore.authorize(tokenId.raw(), toolId, ttl);
        } catch (RuntimeException exception) {
            throw authorizationStoreUnavailable(
                    exception,
                    "AUTHORIZE_CONVERSATION_TOOL",
                    tokenId,
                    toolId);
        }
        audit("CONVERSATION_AUTHORIZED", tokenId, toolId, Map.of("status", AuthorizationStatus.AUTHORIZED.name()));
        return new ConversationAuthorizationResult(AuthorizationStatus.AUTHORIZED, tokenId.raw(), toolId);
    }

    public BatchConversationAuthorizationResult authorizeBatch(String tokenIdRaw, List<String> toolIds) {
        return authorizeBatch(tokenIdRaw, toolIds, null);
    }

    public BatchConversationAuthorizationResult authorizeBatch(String tokenIdRaw, List<String> toolIds, Long expiresInSeconds) {
        TokenId tokenId = parseForRequest(tokenIdRaw);
        Duration ttl = effectiveTtl(expiresInSeconds);
        List<String> distinctToolIds = validateAndNormalizeToolIds(toolIds);

        for (String toolId : distinctToolIds) {
            ToolPolicy policy = findPolicy(tokenId, toolId)
                    .orElseThrow(() -> new ApiException(ErrorCode.TOOL_NOT_BOUND, "tool is not bound"));
            if (policy.effectiveAuthMode() == AuthMode.NO_AUTH_REQUIRED) {
                throw new ApiException(ErrorCode.AUTHORIZATION_NOT_REQUIRED, "authorization is not required");
            }
        }

        try {
            for (String toolId : distinctToolIds) {
                authorizationStore.authorize(tokenId.raw(), toolId, ttl);
            }
        } catch (RuntimeException exception) {
            throw authorizationStoreUnavailable(
                    exception,
                    "AUTHORIZE_CONVERSATION_TOOLS",
                    tokenId,
                    null);
        }

        audit("CONVERSATION_AUTHORIZED_BATCH", tokenId, null, Map.of(
                "status", AuthorizationStatus.AUTHORIZED.name(),
                "toolCount", Integer.toString(distinctToolIds.size())));
        return new BatchConversationAuthorizationResult(
                AuthorizationStatus.AUTHORIZED,
                tokenId.raw(),
                distinctToolIds.size(),
                distinctToolIds);
    }

    private Duration effectiveTtl(Long expiresInSeconds) {
        if (expiresInSeconds == null) {
            return authorizationTtl;
        }
        if (expiresInSeconds <= 0) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "expiresInSeconds must be greater than 0");
        }
        Duration ttl = Duration.ofSeconds(expiresInSeconds);
        if (ttl.compareTo(maxAuthorizationTtl) > 0) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "expiresInSeconds must not exceed max ttl");
        }
        return ttl;
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
            throw authorizationStoreUnavailable(
                    exception,
                    "QUERY_CONVERSATION_AUTHORIZATION_STATUS",
                    tokenId,
                    toolId);
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
            throw authorizationStoreUnavailable(
                    exception,
                    "CLEANUP_CONVERSATION_AUTHORIZATIONS",
                    tokenId,
                    null);
        }
    }

    private Optional<ToolPolicy> findPolicy(TokenId tokenId, String toolId) {
        try {
            return policyRepository.findByAgentIdAndToolId(tokenId.agentId(), toolId);
        } catch (RuntimeException exception) {
            throw new ApiException(
                    ErrorCode.POLICY_STORE_UNAVAILABLE,
                    "policy store is unavailable",
                    exception,
                    context("FIND_TOOL_POLICY", tokenId, toolId));
        }
    }

    private ApiException authorizationStoreUnavailable(
            RuntimeException exception,
            String operation,
            TokenId tokenId,
            String toolId) {
        return new ApiException(
                ErrorCode.AUTHORIZATION_STORE_UNAVAILABLE,
                "authorization store is unavailable",
                exception,
                context(operation, tokenId, toolId));
    }

    private List<String> validateAndNormalizeToolIds(List<String> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "toolIds must not be empty");
        }
        if (toolIds.size() > MAX_BATCH_TOOL_COUNT) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "toolIds size must not exceed 100");
        }

        LinkedHashSet<String> distinctToolIds = new LinkedHashSet<>();
        for (String toolId : toolIds) {
            if (toolId == null || toolId.isBlank()) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "toolId must not be blank");
            }
            if (toolId.length() > MAX_TOOL_ID_LENGTH) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "toolId length must not exceed 255");
            }
            if (!distinctToolIds.add(toolId)) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "toolId must not be duplicated");
            }
        }
        return new ArrayList<>(distinctToolIds);
    }

    private Map<String, String> context(String operation, TokenId tokenId, String toolId) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("operation", operation);
        fields.put("tokenId", tokenId.raw());
        fields.put("agentId", tokenId.agentId());
        fields.put("userId", tokenId.userId());
        fields.put("conversationId", tokenId.conversationId());
        if (toolId != null) {
            fields.put("toolId", toolId);
        }
        return fields;
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
