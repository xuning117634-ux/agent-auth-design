package com.huawei.it.roma.liveeda.policycenter.service;

import com.huawei.it.roma.liveeda.policycenter.audit.AuditLogger;
import com.huawei.it.roma.liveeda.policycenter.audit.NoopAuditLogger;
import com.huawei.it.roma.liveeda.policycenter.domain.AuthMode;
import com.huawei.it.roma.liveeda.policycenter.domain.AuthorizationDecision;
import com.huawei.it.roma.liveeda.policycenter.domain.DecisionReason;
import com.huawei.it.roma.liveeda.policycenter.domain.InvalidTokenIdException;
import com.huawei.it.roma.liveeda.policycenter.domain.TokenId;
import com.huawei.it.roma.liveeda.policycenter.domain.ToolPolicy;
import com.huawei.it.roma.liveeda.policycenter.repository.ToolPolicyRepository;
import com.huawei.it.roma.liveeda.policycenter.store.ConversationAuthorizationStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class AuthorizationDecisionService {

    private final ToolPolicyRepository toolPolicyRepository;
    private final ConversationAuthorizationStore authorizationStore;
    private final ToolUserPolicyEvaluator toolUserPolicyEvaluator;
    private final AuditLogger auditLogger;

    public AuthorizationDecisionService(
            ToolPolicyRepository toolPolicyRepository,
            ConversationAuthorizationStore authorizationStore) {
        this(toolPolicyRepository, authorizationStore, ToolUserPolicyEvaluator.allowAll(), NoopAuditLogger.INSTANCE);
    }

    public AuthorizationDecisionService(
            ToolPolicyRepository toolPolicyRepository,
            ConversationAuthorizationStore authorizationStore,
            AuditLogger auditLogger) {
        this(toolPolicyRepository, authorizationStore, ToolUserPolicyEvaluator.allowAll(), auditLogger);
    }

    @Autowired
    public AuthorizationDecisionService(
            ToolPolicyRepository toolPolicyRepository,
            ConversationAuthorizationStore authorizationStore,
            ToolUserPolicyEvaluator toolUserPolicyEvaluator,
            AuditLogger auditLogger) {
        this.toolPolicyRepository = toolPolicyRepository;
        this.authorizationStore = authorizationStore;
        this.toolUserPolicyEvaluator = toolUserPolicyEvaluator;
        this.auditLogger = auditLogger;
    }

    public AuthorizationDecision decide(String tokenIdRaw, String toolId) {
        TokenId tokenId;
        try {
            tokenId = TokenId.parse(tokenIdRaw);
        } catch (InvalidTokenIdException exception) {
            return auditAndReturn(tokenIdRaw, null, toolId,
                    AuthorizationDecision.deny(DecisionReason.INVALID_TOKEN_ID));
        }

        Optional<ToolPolicy> policy;
        try {
            policy = toolPolicyRepository.findByAgentIdAndToolId(tokenId.agentId(), toolId);
        } catch (RuntimeException exception) {
            log.warn("AUTHORIZATION_DECISION_FAIL_CLOSED tokenId={} agentId={} userId={} conversationId={} toolId={} reason={}",
                    tokenId.raw(),
                    tokenId.agentId(),
                    tokenId.userId(),
                    tokenId.conversationId(),
                    toolId,
                    DecisionReason.POLICY_STORE_UNAVAILABLE,
                    exception);
            return auditAndReturn(tokenId.raw(), tokenId.agentId(), toolId,
                    AuthorizationDecision.deny(DecisionReason.POLICY_STORE_UNAVAILABLE));
        }

        if (policy.isEmpty()) {
            return auditAndReturn(tokenId.raw(), tokenId.agentId(), toolId,
                    AuthorizationDecision.deny(DecisionReason.TOOL_NOT_BOUND));
        }

        boolean userCanAccessTool;
        try {
            userCanAccessTool = toolUserPolicyEvaluator.canAccessTool(tokenId.agentId(), toolId, tokenId.userId());
        } catch (RuntimeException exception) {
            log.warn("AUTHORIZATION_DECISION_FAIL_CLOSED tokenId={} agentId={} userId={} conversationId={} toolId={} reason={}",
                    tokenId.raw(),
                    tokenId.agentId(),
                    tokenId.userId(),
                    tokenId.conversationId(),
                    toolId,
                    DecisionReason.POLICY_STORE_UNAVAILABLE,
                    exception);
            return auditAndReturn(tokenId.raw(), tokenId.agentId(), toolId,
                    AuthorizationDecision.deny(DecisionReason.POLICY_STORE_UNAVAILABLE));
        }

        if (!userCanAccessTool) {
            return auditAndReturn(tokenId.raw(), tokenId.agentId(), toolId,
                    AuthorizationDecision.deny(DecisionReason.USER_TOOL_ACCESS_DENIED));
        }

        AuthMode authMode = policy.get().effectiveAuthMode();
        if (authMode == AuthMode.NO_AUTH_REQUIRED) {
            return auditAndReturn(tokenId.raw(), tokenId.agentId(), toolId,
                    AuthorizationDecision.allow(DecisionReason.NO_AUTH_REQUIRED));
        }
        if (authMode == AuthMode.PER_CALL_AUTH_REQUIRED) {
            return decidePerCallAuthorization(tokenId, toolId);
        }

        boolean authorized;
        try {
            authorized = authorizationStore.exists(tokenId.raw(), toolId);
        } catch (RuntimeException exception) {
            log.warn("AUTHORIZATION_DECISION_FAIL_CLOSED tokenId={} agentId={} userId={} conversationId={} toolId={} reason={}",
                    tokenId.raw(),
                    tokenId.agentId(),
                    tokenId.userId(),
                    tokenId.conversationId(),
                    toolId,
                    DecisionReason.AUTHORIZATION_STORE_UNAVAILABLE,
                    exception);
            return auditAndReturn(tokenId.raw(), tokenId.agentId(), toolId,
                    AuthorizationDecision.deny(DecisionReason.AUTHORIZATION_STORE_UNAVAILABLE));
        }

        if (authorized) {
            return auditAndReturn(tokenId.raw(), tokenId.agentId(), toolId,
                    AuthorizationDecision.allow(DecisionReason.CONVERSATION_AUTHORIZED));
        }
        return auditAndReturn(tokenId.raw(), tokenId.agentId(), toolId,
                AuthorizationDecision.authorizationRequired());
    }

    private AuthorizationDecision decidePerCallAuthorization(TokenId tokenId, String toolId) {
        boolean authorized;
        try {
            authorized = authorizationStore.consume(tokenId.raw(), toolId);
        } catch (RuntimeException exception) {
            log.warn("AUTHORIZATION_DECISION_FAIL_CLOSED tokenId={} agentId={} userId={} conversationId={} toolId={} reason={}",
                    tokenId.raw(),
                    tokenId.agentId(),
                    tokenId.userId(),
                    tokenId.conversationId(),
                    toolId,
                    DecisionReason.AUTHORIZATION_STORE_UNAVAILABLE,
                    exception);
            return auditAndReturn(tokenId.raw(), tokenId.agentId(), toolId,
                    AuthorizationDecision.deny(DecisionReason.AUTHORIZATION_STORE_UNAVAILABLE));
        }
        if (authorized) {
            return auditAndReturn(tokenId.raw(), tokenId.agentId(), toolId,
                    AuthorizationDecision.allow(DecisionReason.PER_CALL_AUTHORIZED));
        }
        return auditAndReturn(tokenId.raw(), tokenId.agentId(), toolId,
                AuthorizationDecision.authorizationRequired(DecisionReason.PER_CALL_AUTHORIZATION_REQUIRED));
    }

    private AuthorizationDecision auditAndReturn(
            String tokenId,
            String agentId,
            String toolId,
            AuthorizationDecision decision) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("eventType", "AUTHORIZATION_DECISION");
        putIfPresent(fields, "tokenId", tokenId);
        putIfPresent(fields, "agentId", agentId);
        putIfPresent(fields, "toolId", toolId);
        fields.put("decision", decision.decision().name());
        fields.put("reason", decision.reason().name());
        auditLogger.record("AUTHORIZATION_DECISION", fields);
        return decision;
    }

    private void putIfPresent(Map<String, String> fields, String key, String value) {
        if (value != null) {
            fields.put(key, value);
        }
    }
}
